package dev.bongballe.parkbuddy.data.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ReminderNotificationManagerImpl(private val context: Context) : ReminderNotificationManager {

  private val notificationManager = NotificationManagerCompat.from(context)

  companion object {
    private const val CHANNEL_ID = "parking_reminders"
    private const val NOTIFICATION_ID_SPOT_FOUND = 1001
    private const val NOTIFICATION_ID_LOCATION_FAILURE = 1002
    private const val NOTIFICATION_ID_MATCH_FAILURE = 1003
  }

  private val contentIntent: PendingIntent? by lazy {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (intent != null) {
      PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    } else {
      null
    }
  }

  override fun showSpotFoundNotification(
    locationName: String,
    nextCleaningText: String,
    bigText: String,
  ) {
    createNotificationChannel()
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(dev.bongballe.parkbuddy.theme.R.drawable.ic_parkbuddy_logo)
        .setContentTitle("Parked on $locationName")
        .setContentText("Next cleaning: $nextCleaningText")
        .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .build()

    notify(NOTIFICATION_ID_SPOT_FOUND, notification)
  }

  override fun sendLocationFailureNotification() {
    createNotificationChannel()
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(dev.bongballe.parkbuddy.theme.R.drawable.ic_parkbuddy_logo)
        .setContentTitle("Failed to get parking location")
        .setContentText("Could not determine your location after disconnecting from Bluetooth.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .build()

    notify(NOTIFICATION_ID_LOCATION_FAILURE, notification)
  }

  override fun sendParkingMatchFailureNotification() {
    createNotificationChannel()
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(dev.bongballe.parkbuddy.theme.R.drawable.ic_parkbuddy_logo)
        .setContentTitle("Parked in Unknown Location")
        .setContentText("We couldn't match your location to a known parking spot.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .build()

    notify(NOTIFICATION_ID_MATCH_FAILURE, notification)
  }

  override fun cancelAll() {
    notificationManager.cancel(NOTIFICATION_ID_SPOT_FOUND)
  }

  private fun createNotificationChannel() {
    val channel =
      NotificationChannel(CHANNEL_ID, "Parking Reminders", NotificationManager.IMPORTANCE_HIGH)
    notificationManager.createNotificationChannel(channel)
  }

  private fun notify(id: Int, notification: android.app.Notification) {
    try {
      if (
        ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      ) {
        notificationManager.notify(id, notification)
      }
    } catch (ignore: SecurityException) {}
  }
}
