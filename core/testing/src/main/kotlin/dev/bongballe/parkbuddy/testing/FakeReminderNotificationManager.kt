package dev.bongballe.parkbuddy.testing

import dev.bongballe.parkbuddy.data.repository.ReminderNotificationManager

class FakeReminderNotificationManager : ReminderNotificationManager {
  var lastSpotFoundNotification: Triple<String, String, String>? = null
  var locationFailureNotificationSent = false
  var parkingMatchFailureNotificationSent = false
  var cancelAllCalled = false

  override fun showSpotFoundNotification(
    title: String,
    contentText: String,
    bigText: String
  ) {
    lastSpotFoundNotification = Triple(title, contentText, bigText)
  }

  override fun sendLocationFailureNotification() {
    locationFailureNotificationSent = true
  }

  override fun sendParkingMatchFailureNotification() {
    parkingMatchFailureNotificationSent = true
  }

  override fun cancelAll() {
    cancelAllCalled = true
  }
}
