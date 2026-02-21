package dev.bongballe.parkbuddy.data.repository

import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.model.Location
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

    val parkingManager = ParkingManager(
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
  fun `processParkingEvent success when matching spot found`() = runTest {
    val context = TestContext()
    val spotLocation = Location(37.7749, -122.4194)
    context.locationRepository.locationResult = Result.success(spotLocation)
    context.parkingRepository.setUserPermitZone("A")

    val spot = createTestSpot(
      id = "1",
      zone = "A",
      lat = spotLocation.latitude,
      lng = spotLocation.longitude,
    )
    context.parkingRepository.setSpots(listOf(spot))

    context.parkingManager.processParkingEvent()
    
    assertThat(context.preferencesRepository.parkedLocation.value?.spotId).isEqualTo("1")
    assertThat(context.reminderRepository.scheduledSpot).isEqualTo(spot)
    assertThat(context.reminderRepository.lastShowNotificationValue).isTrue()
  }

  @Test
  fun `processParkingEvent failure when spot is too far`() = runTest {
    val context = TestContext()
    val myLocation = Location(37.7749, -122.4194)
    context.locationRepository.locationResult = Result.success(myLocation)
    context.parkingRepository.setUserPermitZone("A")

    // Spot is far away
    val spot = createTestSpot(id = "1", zone = "A", lat = 38.0, lng = -123.0)
    context.parkingRepository.setSpots(listOf(spot))

    context.parkingManager.processParkingEvent()

    assertThat(context.notificationManager.parkingMatchFailureNotificationSent).isTrue()
  }

  @Test
  fun `processParkingEvent does nothing when location permission is denied`() = runTest {
    val context = TestContext()
    context.locationRepository.locationResult =
      Result.failure(LocationRepository.PermissionException())

    context.parkingManager.processParkingEvent()

    assertThat(context.notificationManager.locationFailureNotificationSent).isFalse()
    assertThat(context.notificationManager.parkingMatchFailureNotificationSent).isFalse()
  }

  @Test
  fun `processParkingEvent does nothing when no permit zone is set`() = runTest {
    val context = TestContext()
    context.locationRepository.locationResult = Result.success(Location(37.7749, -122.4194))
    context.parkingRepository.setUserPermitZone(null)

    context.parkingManager.processParkingEvent()

    assertThat(context.notificationManager.parkingMatchFailureNotificationSent).isFalse()
  }

  @Test
  fun `processParkingEvent picks closest spot when multiple match`() = runTest {
    val context = TestContext()
    val myLocation = Location(37.7749, -122.4194)
    context.locationRepository.locationResult = Result.success(myLocation)
    context.parkingRepository.setUserPermitZone("A")

    // Closer spot (approx 5m away)
    val spot1 = createTestSpot(id = "1", zone = "A", lat = 37.77494, lng = -122.4194)
    // Farther spot (approx 15m away)
    val spot2 = createTestSpot(id = "2", zone = "A", lat = 37.77503, lng = -122.4194)

    context.parkingRepository.setSpots(listOf(spot1, spot2))

    context.parkingManager.processParkingEvent()

    assertThat(context.preferencesRepository.parkedLocation.value?.spotId).isEqualTo("1")
  }

  @Test
  fun `processParkingEvent fails when spot is exactly on threshold`() = runTest {
    val context = TestContext()
    // 20 meters in degrees is roughly 0.00018 degrees
    val myLocation = Location(37.7749, -122.4194)
    context.locationRepository.locationResult = Result.success(myLocation)
    context.parkingRepository.setUserPermitZone("A")

    // The code uses `distance < thresholdMeters`, so 20.0 should fail
    // Lat 37.7749 + 0.00018 is approx 20m
    val spot = createTestSpot(id = "1", zone = "A", lat = 37.7749 + 0.00018, lng = -122.4194)
    context.parkingRepository.setSpots(listOf(spot))

    context.parkingManager.processParkingEvent()

    // It should fail because 20.0 is not < 20.0
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
    // The center should be average of (37.7749, -122.4194) and (37.77491, -122.41939) from createTestSpot
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
