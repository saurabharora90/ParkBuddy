package dev.bongballe.parkbuddy.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosReminderNotificationManagerImpl : ReminderNotificationManager {

  private val center = UNUserNotificationCenter.currentNotificationCenter()

  override fun showSpotFoundNotification(title: String, contentText: String, bigText: String) {
    postNotification(NOTIFICATION_ID_SPOT_FOUND, title, bigText)
  }

  override fun sendLocationFailureNotification() {
    postNotification(
      NOTIFICATION_ID_LOCATION_FAILURE,
      "Failed to get parking location",
      "Could not determine your location after disconnecting from Bluetooth.",
    )
  }

  override fun sendParkingMatchFailureNotification() {
    postNotification(
      NOTIFICATION_ID_MATCH_FAILURE,
      "Parked in Unknown Location",
      "We couldn't match your location to a known parking spot.",
    )
  }

  override fun cancelAll() {
    center.removePendingNotificationRequestsWithIdentifiers(listOf(NOTIFICATION_ID_SPOT_FOUND))
    center.removeDeliveredNotificationsWithIdentifiers(listOf(NOTIFICATION_ID_SPOT_FOUND))
  }

  private fun postNotification(identifier: String, title: String, body: String) {
    val content =
      UNMutableNotificationContent().apply {
        setTitle(title)
        setBody(body)
        setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
      }
    val request = UNNotificationRequest.requestWithIdentifier(identifier, content, trigger = null)
    center.addNotificationRequest(request, withCompletionHandler = null)
  }

  companion object {
    private const val NOTIFICATION_ID_SPOT_FOUND = "parkbuddy_spot_found"
    private const val NOTIFICATION_ID_LOCATION_FAILURE = "parkbuddy_location_failure"
    private const val NOTIFICATION_ID_MATCH_FAILURE = "parkbuddy_match_failure"
  }
}
