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
    val locationResult = locationRepository.getCurrentLocation()
    val location = locationResult.getOrNull()
    if (location == null) {
      if (locationResult.exceptionOrNull() is LocationRepository.PermissionException) {
        analyticsTracker.logEvent("parking_event_no_permission")
      } else if (locationResult.exceptionOrNull() is LocationRepository.EmptyLocation) {
        analyticsTracker.logEvent("parking_event_location_empty")
        notificationManager.sendLocationFailureNotification()
      }
    } else {
      val userZone = repository.getUserPermitZone().first()
      if (userZone == null) {
        analyticsTracker.logEvent("parking_event_no_zone")
        return
      }

      val watchedSpots = repository.getSpotsByZone(userZone).first()
      if (watchedSpots.isEmpty()) {
        analyticsTracker.logEvent("parking_event_empty_watchlist", mapOf("zone" to userZone))
        notificationManager.sendParkingMatchFailureNotification()
        return
      }

      val matchingSpot = findMatchingSpot(location, watchedSpots)

      if (matchingSpot != null) {
        analyticsTracker.logEvent("parking_event_success")
        park(spot = matchingSpot, detectedLocation = location, showNotification = true)
      } else {
        analyticsTracker.logEvent("parking_event_no_match")
        notificationManager.sendParkingMatchFailureNotification()
      }
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
    showNotification: Boolean
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

  private fun findMatchingSpot(location: Location, spots: List<ParkingSpot>): ParkingSpot? {
    val thresholdMeters = 20.0
    return spots
      .map { spot ->
        spot to
          LocationUtils.calculateDistanceToPolyline(
            location.latitude,
            location.longitude,
            spot.geometry,
          )
      }
      .filter { (_, distance) -> distance < thresholdMeters }
      .minByOrNull { (_, distance) -> distance }
      ?.first
  }
}
