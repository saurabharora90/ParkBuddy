package dev.bongballe.parkbuddy.data.repository

import dev.bongballe.parkbuddy.analytics.AnalyticsTracker
import dev.bongballe.parkbuddy.data.repository.utils.LocationUtils
import dev.bongballe.parkbuddy.model.Location
import dev.bongballe.parkbuddy.model.ParkedLocation
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ParkingType
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
    } else {
      val userZone = repository.getUserPermitZone().first()
      val permitSpots =
        if (userZone != null) repository.getSpotsByZone(userZone).first() else emptyList()

      var matchingSpot = findMatchingSpot(location, permitSpots)
      var parkingType = ParkingType.PERMIT

      if (matchingSpot == null) {
        // Fallback to searching all parkable spots
        val allSpots = repository.getAllSpots().first()
        matchingSpot = findMatchingSpot(location, allSpots)
        if (matchingSpot != null) {
          parkingType = if (matchingSpot.timeLimitHours != null) {
            ParkingType.TIMED
          } else {
            ParkingType.UNRESTRICTED
          }
        }
      }

      if (matchingSpot != null) {
        analyticsTracker.logEvent("parking_event_success", mapOf("type" to parkingType.name))
        park(
          spot = matchingSpot,
          detectedLocation = location,
          showNotification = true,
          type = parkingType,
        )
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

    val userZone = repository.getUserPermitZone().first()
    val type = if (spot.rppArea != null && spot.rppArea == userZone) {
      ParkingType.PERMIT
    } else if (spot.timeLimitHours != null) {
      ParkingType.TIMED
    } else {
      ParkingType.UNRESTRICTED
    }

    park(spot = spot, detectedLocation = location, showNotification = false, type = type)
  }

  private suspend fun park(
    spot: ParkingSpot,
    detectedLocation: Location,
    showNotification: Boolean,
    type: ParkingType
  ) {
    val parkedLocation =
      ParkedLocation(
        spotId = spot.objectId,
        location = detectedLocation,
        parkedAt = Clock.System.now(),
        parkingType = type,
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
