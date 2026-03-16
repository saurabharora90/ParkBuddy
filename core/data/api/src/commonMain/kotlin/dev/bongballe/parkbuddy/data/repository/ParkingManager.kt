package dev.bongballe.parkbuddy.data.repository

import dev.bongballe.parkbuddy.analytics.AnalyticsTracker
import dev.bongballe.parkbuddy.data.repository.utils.LocationUtils
import dev.bongballe.parkbuddy.model.Location
import dev.bongballe.parkbuddy.model.ParkedLocation
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlinx.coroutines.flow.first

@SingleIn(AppScope::class)
@Inject
class ParkingManager(
  private val locationRepository: LocationRepository,
  private val repository: ParkingRepository,
  private val preferencesRepository: PreferencesRepository,
  private val reminderRepository: ReminderRepository,
  private val notificationManager: ReminderNotificationManager,
  private val analyticsTracker: AnalyticsTracker,
) {

  suspend fun processParkingEvent() {
    // Remove previous parking information
    markCarMoved()

    val locationResult = locationRepository.getCurrentLocation()
    val location = locationResult.getOrNull()
    if (location == null) {
      if (locationResult.exceptionOrNull() is LocationRepository.PermissionException) {
        analyticsTracker.logEvent("parking_event_no_permission")
      } else if (locationResult.exceptionOrNull() is LocationRepository.EmptyLocation) {
        analyticsTracker.logEvent("parking_event_location_empty")
        notificationManager.sendLocationFailureNotification()
      }
      return
    }

    val userZone = repository.getUserPermitZone().first()
    val permitSpots =
      if (userZone != null) repository.getSpotsByZone(userZone).first() else emptyList()

    var matchingSpot = findMatchingSpot(location, permitSpots)

    if (matchingSpot == null) {
      val allSpots = repository.getAllSpots().first()
      matchingSpot = findMatchingSpot(location, allSpots)
    }

    if (matchingSpot != null) {
      analyticsTracker.logEvent(
        "parking_event_success",
        mapOf("has_time_limit" to matchingSpot.timeline.isNotEmpty().toString()),
      )
      park(spot = matchingSpot, detectedLocation = location, showNotification = true)
    } else {
      analyticsTracker.logEvent("parking_event_no_match")
      notificationManager.sendParkingMatchFailureNotification()
    }
  }

  suspend fun parkHere(spot: ParkingSpot) {
    val centerLatitude = spot.geometry.coordinates.map { it[1] }.average()
    val centerLongitude = spot.geometry.coordinates.map { it[0] }.average()
    val location = Location(centerLatitude, centerLongitude)
    park(spot = spot, detectedLocation = location, showNotification = false)
  }

  private suspend fun park(
    spot: ParkingSpot,
    detectedLocation: Location,
    showNotification: Boolean,
  ) {
    val parkedLocation =
      ParkedLocation(
        spotId = spot.objectId,
        location = detectedLocation,
        parkedAt = Clock.System.now(),
      )

    preferencesRepository.setParkedLocation(parkedLocation)
    reminderRepository.scheduleReminders(spot, showNotification)
  }

  suspend fun markCarMoved() {
    preferencesRepository.clearParkedLocation()
    reminderRepository.clearAllReminders()
  }

  /**
   * Finds the best matching parking spot for the given location.
   *
   * Implementation uses "Curbside Snapping" logic:
   * 1. Find all candidate spots within 7m (standard street-width safety threshold).
   * 2. Select the closest match.
   *
   * Because the database contains distinct lines for each curb, the closest line is mathematically
   * the correct side of the street.
   */
  private fun findMatchingSpot(location: Location, spots: List<ParkingSpot>): ParkingSpot? {
    val thresholdMeters = 7.0

    return spots
      .map { spot ->
        val distance =
          LocationUtils.calculateDistanceToPolyline(
            location.latitude,
            location.longitude,
            spot.geometry,
          )
        spot to distance
      }
      .filter { (_, distance) -> distance < thresholdMeters }
      .minByOrNull { (_, distance) -> distance }
      ?.first
  }
}
