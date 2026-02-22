package dev.bongballe.parkbuddy.data.repository

interface ReminderNotificationManager {
  fun showSpotFoundNotification(
    title: String,
    contentText: String,
    bigText: String
  )
  fun sendLocationFailureNotification()
  fun sendParkingMatchFailureNotification()
  fun cancelAll()
}