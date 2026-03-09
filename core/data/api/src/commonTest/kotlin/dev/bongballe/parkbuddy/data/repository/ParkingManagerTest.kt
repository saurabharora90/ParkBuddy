package dev.bongballe.parkbuddy.data.repository

import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.model.Location
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.testing.FakeAnalyticsTracker
import dev.bongballe.parkbuddy.testing.FakeLocationRepository
import dev.bongballe.parkbuddy.testing.FakeParkingRepository
import dev.bongballe.parkbuddy.testing.FakePreferencesRepository
import dev.bongballe.parkbuddy.testing.FakeReminderNotificationManager
import dev.bongballe.parkbuddy.testing.FakeReminderRepository
import dev.bongballe.parkbuddy.testing.createTestSpot
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ParkingManagerTest {

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
    val userLocation = Location(37.77487, -122.41937)
    context.locationRepository.locationResult = Result.success(userLocation)
    context.parkingRepository.setUserPermitZone("A")

    val spot =
      createTestSpot(id = "1", zone = "A", lat = 37.7749, lng = -122.4194, side = StreetSide.RIGHT)
    context.parkingRepository.setSpots(listOf(spot))

    context.parkingManager.processParkingEvent()

    val parkedLocation = context.preferencesRepository.parkedLocation.value
    assertThat(parkedLocation?.spotId).isEqualTo("1")
  }

  @Test
  fun `processParkingEvent success when matching spot found outside permit zone`() = runTest {
    val context = TestContext()
    val userLocation = Location(37.77487, -122.41937)
    context.locationRepository.locationResult = Result.success(userLocation)
    context.parkingRepository.setUserPermitZone("B") // Different zone

    val spot =
      createTestSpot(id = "1", zone = "A", lat = 37.7749, lng = -122.4194, side = StreetSide.RIGHT)
    context.parkingRepository.setSpots(listOf(spot))

    context.parkingManager.processParkingEvent()

    val parkedLocation = context.preferencesRepository.parkedLocation.value
    assertThat(parkedLocation?.spotId).isEqualTo("1")
    assertThat(context.reminderRepository.scheduledSpot).isEqualTo(spot)
  }

  @Test
  fun `processParkingEvent success when matching spot found and no permit zone set`() = runTest {
    val context = TestContext()
    val userLocation = Location(37.77487, -122.41937)
    context.locationRepository.locationResult = Result.success(userLocation)
    context.parkingRepository.setUserPermitZone(null)

    val spot =
      createTestSpot(id = "1", zone = "A", lat = 37.7749, lng = -122.4194, side = StreetSide.RIGHT)
    context.parkingRepository.setSpots(listOf(spot))

    context.parkingManager.processParkingEvent()

    val parkedLocation = context.preferencesRepository.parkedLocation.value
    assertThat(parkedLocation?.spotId).isEqualTo("1")
  }

  @Test
  fun `processParkingEvent success and unrestricted when no time limit and no permit`() = runTest {
    val context = TestContext()
    val userLocation = Location(37.77487, -122.41937)
    context.locationRepository.locationResult = Result.success(userLocation)
    context.parkingRepository.setUserPermitZone(null)

    val spot =
      createTestSpot(
        id = "1",
        zone = null,
        lat = 37.7749,
        lng = -122.4194,
        side = StreetSide.RIGHT,
        timedRestriction = null,
      )
    context.parkingRepository.setSpots(listOf(spot))

    context.parkingManager.processParkingEvent()

    val parkedLocation = context.preferencesRepository.parkedLocation.value
    assertThat(parkedLocation?.spotId).isEqualTo("1")
  }

  @Test
  fun `processParkingEvent picks closest spot when multiple match`() = runTest {
    val context = TestContext()
    val myLocation = Location(37.77487, -122.41937)
    context.locationRepository.locationResult = Result.success(myLocation)
    context.parkingRepository.setUserPermitZone("A")

    // Spot 1 is extremely close (Winner)
    val spot1 =
      createTestSpot(
        id = "1",
        zone = "A",
        lat = 37.774871,
        lng = -122.419371,
        side = StreetSide.RIGHT,
      )
    // Spot 2 is a few meters away
    val spot2 =
      createTestSpot(id = "2", zone = "A", lat = 37.7750, lng = -122.4195, side = StreetSide.RIGHT)

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

    val spot = createTestSpot(id = "1", lat = 37.7749, lng = -122.4194)
    context.parkingRepository.setSpots(listOf(spot))

    context.parkingManager.processParkingEvent()

    // It should fail because 14m > 7m threshold.
    assertThat(context.preferencesRepository.parkedLocation.value).isNull()
    assertThat(context.notificationManager.parkingMatchFailureNotificationSent).isTrue()
  }

  @Test
  fun `parkHere uses spot center and suppresses notification`() = runTest {
    val context = TestContext()
    val spot = createTestSpot(id = "1", zone = "A", lat = 37.7749, lng = -122.4194)

    context.parkingManager.parkHere(spot)

    val parkedLocation = context.preferencesRepository.parkedLocation.value
    assertThat(parkedLocation?.spotId).isEqualTo("1")
    assertThat(parkedLocation?.location?.latitude).isWithin(0.000001).of(37.774905)
    assertThat(parkedLocation?.location?.longitude).isWithin(0.000001).of(-122.419395)
    assertThat(context.reminderRepository.lastShowNotificationValue).isFalse()
  }

  @Test
  fun `markCarMoved clears data`() = runTest {
    val context = TestContext()
    context.parkingManager.markCarMoved()

    assertThat(context.preferencesRepository.parkedLocation.value).isNull()
    assertThat(context.reminderRepository.clearAllRemindersCalled).isTrue()
  }
}
