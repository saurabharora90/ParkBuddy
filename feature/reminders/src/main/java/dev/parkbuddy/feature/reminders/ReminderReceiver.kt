package dev.parkbuddy.feature.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.bongballe.parkbuddy.qualifier.WithScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.BroadcastReceiverKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class, binding<BroadcastReceiver>())
@BroadcastReceiverKey(ReminderReceiver::class)
@Inject
class ReminderReceiver(
  private val preferencesRepository: PreferencesRepository,
  @WithScope(AppScope::class) private val coroutineScope: CoroutineScope,
  @WithDispatcherType(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val streetName = intent.getStringExtra("streetName") ?: "your parked street"
    val spotId = intent.getStringExtra("spotId")

    val pendingResult = goAsync()
    coroutineScope.launch(ioDispatcher) {
      try {
        val parkedLocation = preferencesRepository.parkedLocation.firstOrNull()
        if (parkedLocation != null && (spotId == null || parkedLocation.spotId == spotId)) {
          showNotification(
            context,
            "Street Cleaning Reminder",
            "Upcoming cleaning on $streetName! Move your car.",
          )
        }
      } finally {
        pendingResult.finish()
      }
    }
  }

  private fun showNotification(context: Context, title: String, message: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (
        ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
      )
        return
    }

    val channelId = "street_cleaning_reminders"
    val notificationManager = NotificationManagerCompat.from(context)

    val channel =
      NotificationChannel(
        channelId,
        "Street Cleaning Reminders",
        NotificationManager.IMPORTANCE_HIGH,
      )
    notificationManager.createNotificationChannel(channel)

    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val contentIntent =
      if (launchIntent != null) {
        PendingIntent.getActivity(
          context,
          0,
          launchIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      } else {
        null
      }

    val notification =
      NotificationCompat.Builder(context, channelId)
        .setSmallIcon(dev.bongballe.parkbuddy.theme.R.drawable.ic_parkbuddy_logo)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
  }
}
