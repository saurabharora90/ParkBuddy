package dev.bongballe.parkbuddy.data.repository

import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.fakes.FakeAnalyticsTracker
import dev.bongballe.parkbuddy.fakes.FakeLocationRepository
import dev.bongballe.parkbuddy.fakes.FakeParkingRepository
import dev.bongballe.parkbuddy.fakes.FakePreferencesRepository
import dev.bongballe.parkbuddy.fakes.FakeReminderNotificationManager
import dev.bongballe.parkbuddy.fakes.FakeReminderRepository
import dev.bongballe.parkbuddy.fixtures.createSpot
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.Location
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.StreetSide
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ParkingManagerTest {

  // ~4m from the default spot coordinates, within the 7m threshold
  private val nearbyUserLocation = Location(37.77487, -122.41937)

  private class TestContext {
    val locationRepository = FakeLocationRepository()
    val parkingRepository = FakeParkingRepository()
    val preferencesRepository = FakePreferencesRepository()
    val reminderRepository = FakeReminderRepository()
    val notificationManager = FakeReminderNotificationManager()
    val analyticsTracker = FakeAnalyticsTracker()

    val parkingManager =
      ParkingManager(
        locationRepository = locationRepository,
        repository = parkingRepository,
        preferencesRepository = preferencesRepository,
        reminderRepository = reminderRepository,
        notificationManager = notificationManager,
        analyticsTracker = analyticsTracker,
      )
  }

  @Test
  fun `processParkingEvent sends location failure notification when location is empty`() = runTest {
    val context = TestContext()
    context.locationRepository.locationResult = Result.failure(LocationRepository.EmptyLocation())

    context.parkingManager.processParkingEvent()

    assertThat(context.notificationManager.locationFailureNotificationSent).isTrue()
  }

  @Test
  fun `processParkingEvent silently returns when location permission denied`() = runTest {
    val context = TestContext()
    context.locationRepository.locationResult =
      Result.failure(LocationRepository.PermissionException())

    context.parkingManager.processParkingEvent()

    assertThat(context.notificationManager.locationFailureNotificationSent).isFalse()
    assertThat(context.notificationManager.parkingMatchFailureNotificationSent).isFalse()
    assertThat(context.preferencesRepository.parkedLocation.value).isNull()
  }

  @Test
  fun `processParkingEvent sends notification when no spots in permit zone`() = runTest {
    val context = TestContext()
    context.locationRepository.locationResult = Result.success(Location(37.7749, -122.4194))
    context.parkingRepository.setUserPermitZone("A")
    context.parkingRepository.setSpots(emptyList())

    context.parkingManager.processParkingEvent()

    assertThat(context.notificationManager.parkingMatchFailureNotificationSent).isTrue()
  }

  @Test
  fun `processParkingEvent success when matching spot found in permit zone`() = runTest {
    val context = TestContext()
    // User is approx 4m from the line
    val userLocation = nearbyUserLocation
    context.locationRepository.locationResult = Result.success(userLocation)
    context.parkingRepository.setUserPermitZone("A")

    val spot =
      createSpot(id = "1", zone = "A", lat = 37.7749, lng = -122.4194, side = StreetSide.RIGHT)
    context.parkingRepository.setSpots(listOf(spot))

    context.parkingManager.processParkingEvent()

    val parkedLocation = context.preferencesRepository.parkedLocation.value
    assertThat(parkedLocation?.spotId).isEqualTo("1")
    assertThat(context.reminderRepository.scheduledSpot).isEqualTo(spot)
  }

  @Test
  fun `processParkingEvent success when matching spot found outside permit zone`() = runTest {
    val context = TestContext()
    val userLocation = nearbyUserLocation
    context.locationRepository.locationResult = Result.success(userLocation)
    context.parkingRepository.setUserPermitZone("B") // Different zone

    val spot =
      createSpot(id = "1", zone = "A", lat = 37.7749, lng = -122.4194, side = StreetSide.RIGHT)
    context.parkingRepository.setSpots(listOf(spot))

    context.parkingManager.processParkingEvent()

    val parkedLocation = context.preferencesRepository.parkedLocation.value
    assertThat(parkedLocation?.spotId).isEqualTo("1")
    assertThat(context.reminderRepository.scheduledSpot).isEqualTo(spot)
  }

  @Test
  fun `processParkingEvent success when matching spot found and no permit zone set`() = runTest {
    val context = TestContext()
    val userLocation = nearbyUserLocation
    context.locationRepository.locationResult = Result.success(userLocation)
    context.parkingRepository.setUserPermitZone(null)

    val spot =
      createSpot(id = "1", zone = "A", lat = 37.7749, lng = -122.4194, side = StreetSide.RIGHT)
    context.parkingRepository.setSpots(listOf(spot))

    context.parkingManager.processParkingEvent()

    val parkedLocation = context.preferencesRepository.parkedLocation.value
    assertThat(parkedLocation?.spotId).isEqualTo("1")
  }

  @Test
  fun `processParkingEvent success and unrestricted when no time limit and no permit`() = runTest {
    val context = TestContext()
    val userLocation = nearbyUserLocation
    context.locationRepository.locationResult = Result.success(userLocation)
    context.parkingRepository.setUserPermitZone(null)

    val spot =
      createSpot(
        id = "1",
        zone = null,
        lat = 37.7749,
        lng = -122.4194,
        side = StreetSide.RIGHT,
        limitMinutes = null,
      )
    context.parkingRepository.setSpots(listOf(spot))

    context.parkingManager.processParkingEvent()

    val parkedLocation = context.preferencesRepository.parkedLocation.value
    assertThat(parkedLocation?.spotId).isEqualTo("1")
  }

  @Test
  fun `processParkingEvent picks closest spot when multiple match`() = runTest {
    val context = TestContext()
    context.locationRepository.locationResult = Result.success(nearbyUserLocation)
    context.parkingRepository.setUserPermitZone("A")

    // Spot 1 is extremely close (Winner)
    val spot1 =
      createSpot(id = "1", zone = "A", lat = 37.774871, lng = -122.419371, side = StreetSide.RIGHT)
    // Spot 2 is a few meters away
    val spot2 =
      createSpot(id = "2", zone = "A", lat = 37.7750, lng = -122.4195, side = StreetSide.RIGHT)

    context.parkingRepository.setSpots(listOf(spot1, spot2))

    context.parkingManager.processParkingEvent()

    assertThat(context.preferencesRepository.parkedLocation.value?.spotId).isEqualTo("1")
  }

  @Test
  fun `processParkingEvent fails when spot is too far away`() = runTest {
    val context = TestContext()
    // User is approx 14m away from base
    val userLocation = Location(37.7748, -122.4193)
    context.locationRepository.locationResult = Result.success(userLocation)

    val spot = createSpot(id = "1", lat = 37.7749, lng = -122.4194)
    context.parkingRepository.setSpots(listOf(spot))

    context.parkingManager.processParkingEvent()

    // It should fail because 14m > 7m threshold.
    assertThat(context.preferencesRepository.parkedLocation.value).isNull()
    assertThat(context.notificationManager.parkingMatchFailureNotificationSent).isTrue()
  }

  @Test
  fun `parkHere uses spot center and suppresses notification`() = runTest {
    val context = TestContext()
    val spot = createSpot(id = "1", zone = "A", lat = 37.7749, lng = -122.4194)

    context.parkingManager.parkHere(spot)

    val parkedLocation = context.preferencesRepository.parkedLocation.value
    assertThat(parkedLocation?.spotId).isEqualTo("1")
    assertThat(parkedLocation?.location?.latitude).isWithin(0.000001).of(37.774905)
    assertThat(parkedLocation?.location?.longitude).isWithin(0.000001).of(-122.419395)
    assertThat(context.reminderRepository.scheduledSpot).isEqualTo(spot)
    assertThat(context.reminderRepository.lastShowNotificationValue).isFalse()
  }

  @Test
  fun `markCarMoved clears data`() = runTest {
    val context = TestContext()
    context.parkingManager.markCarMoved()

    assertThat(context.preferencesRepository.parkedLocation.value).isNull()
    assertThat(context.reminderRepository.clearAllRemindersCalled).isTrue()
  }

  // ── Two-phase matching: centerline + cross product ──
  //
  // These tests simulate a narrow street (e.g., Bryant St or Colin P Kelly Jr) where L and R
  // curbside polylines are close together. The centerline runs west→east at lat 37.775:
  //
  //   North (37.7751)   ← LEFT curbside (offset ~5m north of center)
  //   ──────────────────── centerline at 37.775
  //   South (37.7749)   ← RIGHT curbside (offset ~5m south of center)
  //
  // A GPS point north of center should match LEFT, south should match RIGHT, regardless of which
  // curbside polyline is numerically closer.

  private val narrowStreetCenterline =
    Geometry(
      type = "LineString",
      coordinates =
        listOf(listOf(-122.420, 37.775), listOf(-122.419, 37.775), listOf(-122.418, 37.775)),
    )

  // LEFT curbside: ~5m north of centerline
  private val leftCurbside =
    Geometry(
      type = "LineString",
      coordinates =
        listOf(listOf(-122.420, 37.77504), listOf(-122.419, 37.77504), listOf(-122.418, 37.77504)),
    )

  // RIGHT curbside: ~5m south of centerline
  private val rightCurbside =
    Geometry(
      type = "LineString",
      coordinates =
        listOf(listOf(-122.420, 37.77496), listOf(-122.419, 37.77496), listOf(-122.418, 37.77496)),
    )

  private fun narrowStreetSpots(): Pair<ParkingSpot, ParkingSpot> {
    val leftSpot =
      createSpot(
        id = "left",
        cnn = "999",
        side = StreetSide.LEFT,
        geometry = leftCurbside,
        centerlineGeometry = narrowStreetCenterline,
      )
    val rightSpot =
      createSpot(
        id = "right",
        cnn = "999",
        side = StreetSide.RIGHT,
        geometry = rightCurbside,
        centerlineGeometry = narrowStreetCenterline,
      )
    return leftSpot to rightSpot
  }

  @Test
  fun `findMatchingSpot picks LEFT side when user is north of centerline`() {
    val context = TestContext()
    val (leftSpot, rightSpot) = narrowStreetSpots()

    // User is ~3m north of centerline (on the LEFT side of the street)
    val userLocation = Location(37.77503, -122.419)

    val result = context.parkingManager.findMatchingSpot(userLocation, listOf(leftSpot, rightSpot))

    assertThat(result?.objectId).isEqualTo("left")
  }

  @Test
  fun `findMatchingSpot picks RIGHT side when user is south of centerline`() {
    val context = TestContext()
    val (leftSpot, rightSpot) = narrowStreetSpots()

    // User is ~3m south of centerline (on the RIGHT side of the street)
    val userLocation = Location(37.77497, -122.419)

    val result = context.parkingManager.findMatchingSpot(userLocation, listOf(leftSpot, rightSpot))

    assertThat(result?.objectId).isEqualTo("right")
  }

  @Test
  fun `findMatchingSpot uses cross product even when GPS is closer to wrong curbside`() {
    val context = TestContext()
    val (leftSpot, rightSpot) = narrowStreetSpots()

    // User is barely north of centerline (~1m). On a 10m wide street, the LEFT curbside is ~5m
    // away but the RIGHT curbside is ~4m away. Pure distance matching would pick RIGHT (wrong).
    // Cross product correctly identifies this as LEFT.
    val userLocation = Location(37.77501, -122.419)

    val result = context.parkingManager.findMatchingSpot(userLocation, listOf(leftSpot, rightSpot))

    assertThat(result?.objectId).isEqualTo("left")
  }

  @Test
  fun `findMatchingSpot returns single side when only one exists for CNN`() {
    val context = TestContext()
    val (leftSpot, _) = narrowStreetSpots()

    // Only LEFT side exists. User is on the right side, but we should still return the only
    // available spot.
    val userLocation = Location(37.77497, -122.419)

    val result = context.parkingManager.findMatchingSpot(userLocation, listOf(leftSpot))

    assertThat(result?.objectId).isEqualTo("left")
  }

  @Test
  fun `findMatchingSpot picks closest CNN when multiple streets are nearby`() {
    val context = TestContext()

    // Street A at lat 37.775
    val streetACenterline =
      Geometry("LineString", listOf(listOf(-122.420, 37.775), listOf(-122.418, 37.775)))
    val streetALeft =
      createSpot(
        id = "A-left",
        cnn = "100",
        side = StreetSide.LEFT,
        geometry =
          Geometry("LineString", listOf(listOf(-122.420, 37.77504), listOf(-122.418, 37.77504))),
        centerlineGeometry = streetACenterline,
      )

    // Street B at lat 37.776 (one block north, ~111m away)
    val streetBCenterline =
      Geometry("LineString", listOf(listOf(-122.420, 37.776), listOf(-122.418, 37.776)))
    val streetBLeft =
      createSpot(
        id = "B-left",
        cnn = "200",
        side = StreetSide.LEFT,
        geometry =
          Geometry("LineString", listOf(listOf(-122.420, 37.77604), listOf(-122.418, 37.77604))),
        centerlineGeometry = streetBCenterline,
      )

    // User is near street A
    val userLocation = Location(37.77503, -122.419)

    val result =
      context.parkingManager.findMatchingSpot(userLocation, listOf(streetALeft, streetBLeft))

    assertThat(result?.objectId).isEqualTo("A-left")
  }

  @Test
  fun `findMatchingSpot falls back to curbside distance for spots without centerline`() {
    val context = TestContext()

    // Regulation-only spot with no centerline (e.g., meter_12345)
    val orphanSpot = createSpot(id = "orphan", lat = 37.775, lng = -122.419, cnn = "orphan")

    // User is ~4m away
    val userLocation = Location(37.77504, -122.419)

    val result = context.parkingManager.findMatchingSpot(userLocation, listOf(orphanSpot))

    assertThat(result?.objectId).isEqualTo("orphan")
  }

  @Test
  fun `findMatchingSpot returns null when all spots are too far`() {
    val context = TestContext()
    val (leftSpot, rightSpot) = narrowStreetSpots()

    // User is 50m away
    val userLocation = Location(37.776, -122.419)

    val result = context.parkingManager.findMatchingSpot(userLocation, listOf(leftSpot, rightSpot))

    assertThat(result).isNull()
  }

  @Test
  fun `findMatchingSpot prefers centerline match over curbside fallback`() {
    val context = TestContext()
    val (leftSpot, rightSpot) = narrowStreetSpots()

    // Also add an orphan spot that happens to be slightly closer by curbside distance
    val orphanSpot =
      createSpot(
        id = "orphan",
        cnn = "orphan_cnn",
        geometry =
          Geometry("LineString", listOf(listOf(-122.419, 37.77502), listOf(-122.4189, 37.77502))),
      )

    val userLocation = Location(37.77503, -122.419)

    val result =
      context.parkingManager.findMatchingSpot(userLocation, listOf(leftSpot, rightSpot, orphanSpot))

    // Should pick left via centerline+cross-product, not the orphan via curbside distance
    assertThat(result?.objectId).isEqualTo("left")
  }
}
