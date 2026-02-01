package dev.bongballe.parkbuddy.data.repository

interface ReminderNotificationManager {
  fun showSpotFoundNotification(
    locationName: String,
    nextCleaningText: String,
    bigText: String
  )
  fun sendLocationFailureNotification()
  fun sendParkingMatchFailureNotification()
  fun cancelAll()
}