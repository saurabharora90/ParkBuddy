package dev.bongballe.parkbuddy.data.repository

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ReminderRepositoryImpl(
  private val context: Context,
  private val parkingRepository: ParkingRepository,
) : ReminderRepository {

  private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
  private val notificationManager = NotificationManagerCompat.from(context)

  override suspend fun scheduleReminders(spot: ParkingSpot) {
    clearAllReminders()

    val reminders = parkingRepository.getReminders().first()
    val now = Clock.System.now()

    val nextCleaningTime = spot.sweepingSchedules.mapNotNull { it.nextOccurrence(now) }.minOrNull()

    val streetName = spot.streetName ?: spot.neighborhood ?: "Unknown Street"

    val remindersSet = mutableListOf<String>()

    if (nextCleaningTime != null) {
      reminders
        .sortedBy { it.value }
        .forEach { reminder ->
          val reminderTime = nextCleaningTime - reminder.value.minutes
          if (reminderTime > now) {
            setAlarm(reminderTime, streetName)
            val localTime = reminderTime.toLocalDateTime(TimeZone.currentSystemDefault())
            val hour = localTime.hour
            val minute = localTime.minute
            val amPm = if (hour < 12) "AM" else "PM"
            val displayHour =
              when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
              }
            val displayMinute = minute.toString().padStart(2, '0')
            val dayName = localTime.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }

            remindersSet.add("• $dayName at $displayHour:$displayMinute $amPm")
          }
        }
    }

    showSpotFoundNotification(streetName, spot, nextCleaningTime, remindersSet)
  }

  override suspend fun clearAllReminders() {
    notificationManager.cancel(1001)
  }

  private fun setAlarm(time: Instant, spotName: String) {
    val intent =
      Intent().apply {
        setClassName(context.packageName, "dev.parkbuddy.feature.reminders.ReminderReceiver")
        putExtra("streetName", spotName)
      }

    val pendingIntent =
      PendingIntent.getBroadcast(
        context,
        spotName.hashCode() + time.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val triggerAtMillis = time.toEpochMilliseconds()

    val canScheduleExact =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()
      } else {
        true
      }

    if (canScheduleExact) {
      alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerAtMillis,
        pendingIntent,
      )
    } else {
      alarmManager.setWindow(
        AlarmManager.RTC_WAKEUP,
        triggerAtMillis,
        5 * 60 * 1000L,
        pendingIntent,
      )
    }
  }

  private fun showSpotFoundNotification(
    locationName: String,
    spot: ParkingSpot,
    nextCleaning: Instant?,
    remindersSet: List<String>,
  ) {
    val channelId = "parking_reminders"
    val channel =
      NotificationChannel(channelId, "Parking Reminders", NotificationManager.IMPORTANCE_HIGH)
    notificationManager.createNotificationChannel(channel)

    val nextCleaningText =
      nextCleaning?.let {
        val schedule =
          spot.sweepingSchedules.find { s -> s.nextOccurrence(Clock.System.now()) == it }
        schedule?.formatSchedule() ?: "Upcoming"
      } ?: "No upcoming cleaning found"

    val reminderLines =
      if (remindersSet.isEmpty()) {
        "No reminders set (cleaning too soon or no reminders configured)."
      } else {
        "Reminders set for:\n" + remindersSet.joinToString("\n")
      }

    val bigText = "Next cleaning: $nextCleaningText\n\n$reminderLines"

    val notification =
      NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle("Parked on $locationName")
        .setContentText("Next cleaning: $nextCleaningText")
        .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    try {
      if (
        ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      ) {
        notificationManager.notify(1001, notification)
      }
    } catch (ignore: SecurityException) {}
  }
}
