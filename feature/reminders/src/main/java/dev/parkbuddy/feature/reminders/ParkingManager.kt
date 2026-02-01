package dev.parkbuddy.feature.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.bongballe.parkbuddy.data.repository.ReminderNotificationManager
import dev.bongballe.parkbuddy.data.repository.ReminderRepository
import dev.bongballe.parkbuddy.data.repository.utils.LocationUtils
import dev.bongballe.parkbuddy.model.ParkedLocation
import dev.bongballe.parkbuddy.model.ParkingSpot
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class ParkingManager(
  private val context: Context,
  private val repository: ParkingRepository,
  private val preferencesRepository: PreferencesRepository,
  private val reminderRepository: ReminderRepository,
  private val notificationManager: ReminderNotificationManager,
) {

  suspend fun processParkingEvent() {
    if (
      ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      return
    }

    val locationClient = LocationServices.getFusedLocationProviderClient(context)
    val cts = CancellationTokenSource()

    val location =
      try {
        withTimeout(10_000L) {
          locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
        }
      } catch (e: Exception) {
        null
      }

    if (location == null) {
      notificationManager.sendLocationFailureNotification()
      return
    }

    val userZone = repository.getUserRppZone().first() ?: return
    val watchedSpots = repository.getSpotsByZone(userZone).first()
    if (watchedSpots.isEmpty()) {
      notificationManager.sendParkingMatchFailureNotification()
      return
    }

    val matchingSpot = findMatchingSpot(location, watchedSpots)

    if (matchingSpot != null) {
      val parkedLocation =
        ParkedLocation(
          spotId = matchingSpot.objectId,
          latitude = location.latitude,
          longitude = location.longitude,
          parkedAt = Clock.System.now(),
        )
      preferencesRepository.setParkedLocation(parkedLocation)
      reminderRepository.scheduleReminders(matchingSpot)
    } else {
      notificationManager.sendParkingMatchFailureNotification()
    }
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
