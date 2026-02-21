package dev.bongballe.parkbuddy.testing

import dev.bongballe.parkbuddy.data.repository.ReminderNotificationManager

class FakeReminderNotificationManager : ReminderNotificationManager {
  var lastSpotFoundNotification: Triple<String, String, String>? = null
  var locationFailureNotificationSent = false
  var parkingMatchFailureNotificationSent = false
  var cancelAllCalled = false

  override fun showSpotFoundNotification(
    locationName: String,
    nextCleaningText: String,
    bigText: String
  ) {
    lastSpotFoundNotification = Triple(locationName, nextCleaningText, bigText)
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
