package dev.bongballe.parkbuddy.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {

  private val alarmManager by lazy {
    context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
  }

  override fun setAlarm(
    index: Int,
    triggerAt: Instant,
    spotName: String,
    spotId: String,
    message: String,
  ) {
    val intent =
      Intent().apply {
        setClassName(context.packageName, "dev.parkbuddy.feature.reminders.ReminderReceiver")
        putExtra("streetName", spotName)
        putExtra("spotId", spotId)
        putExtra("cleaningStartTime", message)
      }

    val pendingIntent =
      PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST_CODE_BASE + index,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val triggerAtMillis = triggerAt.toEpochMilliseconds()

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

  override fun cancelAll() {
    for (i in 0 until AlarmScheduler.MAX_REMINDERS) {
      val intent =
        Intent().apply {
          setClassName(context.packageName, "dev.parkbuddy.feature.reminders.ReminderReceiver")
        }
      val pendingIntent =
        PendingIntent.getBroadcast(
          context,
          ALARM_REQUEST_CODE_BASE + i,
          intent,
          PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
      if (pendingIntent != null) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
      }
    }
  }

  companion object {
    private const val ALARM_REQUEST_CODE_BASE = 1000
  }
}
