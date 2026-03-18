package dev.bongballe.parkbuddy.data.repository

import dev.bongballe.parkbuddy.analytics.AnalyticsTracker
import dev.bongballe.parkbuddy.data.repository.utils.LocationUtils
import dev.bongballe.parkbuddy.model.Geometry
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
   * Finds the best matching parking spot for the given location using two-phase matching.
   *
   * Phase 1 ("Which block?"): Find the closest street centerline. This comparison is between
   * different streets/blocks whose centerlines are far apart, so distance works reliably here.
   *
   * Phase 2 ("Which side?"): For the matched CNN, use a cross-product against the centerline to
   * determine LEFT vs RIGHT. The cross product is the correct geometric tool for this because even
   * 1 meter of being on the correct side of the centerline gives the right answer, unlike distance
   * which fails on narrow streets where both curbside lines are within GPS error.
   *
   * Falls back to direct curbside-distance matching for spots without a centerline (e.g., virtual
   * regulation-only or meter-only segments).
   *
   * GPS error cloud ╱ ╲ ────L───●───R──── ← narrow street: L and R curbside lines are ~8m apart │
   * centerline
   *
   * cross product of centerline direction × GPS offset → unambiguous
   */
  internal fun findMatchingSpot(location: Location, spots: List<ParkingSpot>): ParkingSpot? {
    // Centerline threshold is larger because the user is at the curb (~half a street width
    // from center). Wide 4-lane streets can be 15m+ across, plus GPS error.
    val centerlineThresholdMeters = 15.0
    val curbsideThresholdMeters = 7.0

    // Split spots into those with centerline data (can do cross-product side matching)
    // and those without (fall back to direct curbside distance)
    val withCenterline = mutableMapOf<String, MutableList<ParkingSpot>>()
    val withoutCenterline = mutableListOf<ParkingSpot>()

    for (spot in spots) {
      val cnn = spot.sweepingCnn
      if (cnn != null && spot.centerlineGeometry != null) {
        withCenterline.getOrPut(cnn) { mutableListOf() }.add(spot)
      } else {
        withoutCenterline.add(spot)
      }
    }

    // Phase 1: Find the closest centerline among all CNNs
    data class CenterlineMatch(val cnn: String, val distance: Double, val centerline: Geometry)

    val centerlineMatches =
      withCenterline.mapNotNull { (cnn, cnnSpots) ->
        val centerline = cnnSpots.firstOrNull()?.centerlineGeometry ?: return@mapNotNull null
        val dist =
          LocationUtils.calculateDistanceToPolyline(
            location.latitude,
            location.longitude,
            centerline,
          )
        if (dist < centerlineThresholdMeters) CenterlineMatch(cnn, dist, centerline) else null
      }

    // Phase 2: For the closest centerline, use cross product to pick the correct side
    val bestCenterline = centerlineMatches.minByOrNull { it.distance }
    if (bestCenterline != null) {
      val cnnSpots = withCenterline[bestCenterline.cnn].orEmpty()
      if (cnnSpots.size == 1) return cnnSpots.single()

      val determinedSide =
        LocationUtils.determineSide(
          location.latitude,
          location.longitude,
          bestCenterline.centerline,
        )
      val sideMatch = cnnSpots.find { it.sweepingSide == determinedSide }
      if (sideMatch != null) return sideMatch

      return cnnSpots.firstOrNull()
    }

    // Fallback: spots without centerline data use direct curbside distance
    return withoutCenterline
      .map { spot ->
        spot to
          LocationUtils.calculateDistanceToPolyline(
            location.latitude,
            location.longitude,
            spot.geometry,
          )
      }
      .filter { (_, distance) -> distance < curbsideThresholdMeters }
      .minByOrNull { (_, distance) -> distance }
      ?.first
  }
}
