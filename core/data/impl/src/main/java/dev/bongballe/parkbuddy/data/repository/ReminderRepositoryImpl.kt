package dev.bongballe.parkbuddy.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.bongballe.parkbuddy.data.repository.utils.DateTimeUtils
import dev.bongballe.parkbuddy.data.repository.utils.formatWithDate
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ReminderMinutes
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ReminderRepositoryImpl(
  private val context: Context,
  private val notificationManager: ReminderNotificationManager,
) : ReminderRepository {

  private val alarmManager by lazy {
    context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
  }

  override fun getReminders(): Flow<List<ReminderMinutes>> {
    return context.dataStore.data
      .map { pref -> pref[REMINDER_MINUTES]?.mapNotNull { it.toIntOrNull() } ?: emptySet() }
      .map { it.map { ReminderMinutes(it) } }
  }

  override suspend fun addReminder(minutesBefore: ReminderMinutes) {
    val currentReminders = getReminders().first().toMutableSet()
    currentReminders.add(minutesBefore)
    context.dataStore.edit {
      it[REMINDER_MINUTES] = currentReminders.map { it.value.toString() }.toSet()
    }
  }

  override suspend fun removeReminder(minutesBefore: ReminderMinutes) {
    val currentReminders = getReminders().first().toMutableSet()
    currentReminders.remove(minutesBefore)
    context.dataStore.edit {
      it[REMINDER_MINUTES] = currentReminders.map { it.value.toString() }.toSet()
    }
  }

  override suspend fun scheduleReminders(spot: ParkingSpot, showNotification: Boolean) {
    clearAllReminders()

    val reminders = getReminders().first()
    val now = Clock.System.now()

    val nextCleaning =
      spot.sweepingSchedules
        .mapNotNull { schedule -> schedule.nextOccurrence(now)?.let { next -> schedule to next } }
        .minByOrNull { it.second }

    val nextCleaningTime = nextCleaning?.second
    val nextCleaningSchedule = nextCleaning?.first

    val streetName = spot.streetName ?: spot.neighborhood ?: "Unknown Street"

    val remindersSet = mutableListOf<String>()

    if (nextCleaningTime != null && nextCleaningSchedule != null) {
      val cleaningStartTime = DateTimeUtils.formatHour(nextCleaningSchedule.fromHour)
      reminders
        .sortedBy { it.value }
        .take(MAX_REMINDERS)
        .forEachIndexed { index, reminder ->
          val reminderTime = nextCleaningTime - reminder.value.minutes
          if (reminderTime > now) {
            setAlarm(index, reminderTime, streetName, spot.objectId, cleaningStartTime)
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

    if (showNotification)
      showSpotFoundNotification(
        locationName = streetName,
        nextCleaning = nextCleaningTime,
        nextCleaningSchedule = nextCleaningSchedule,
        remindersSet = remindersSet,
      )
  }

  override suspend fun clearAllReminders() {
    notificationManager.cancelAll()
    for (i in 0 until MAX_REMINDERS) {
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

  private fun setAlarm(
    index: Int,
    time: Instant,
    spotName: String,
    spotId: String,
    cleaningStartTime: String,
  ) {
    val intent =
      Intent().apply {
        setClassName(context.packageName, "dev.parkbuddy.feature.reminders.ReminderReceiver")
        putExtra("streetName", spotName)
        putExtra("spotId", spotId)
        putExtra("cleaningStartTime", cleaningStartTime)
      }

    val pendingIntent =
      PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST_CODE_BASE + index,
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
    nextCleaning: Instant?,
    nextCleaningSchedule: SweepingSchedule?,
    remindersSet: List<String>,
  ) {
    val now = Clock.System.now()
    val nextCleaningText =
      nextCleaning?.let {
        val formatted = nextCleaningSchedule?.formatWithDate(it) ?: "Upcoming"

        if ((it - now) < 6.hours) {
          "TODAY ($formatted)"
        } else {
          formatted
        }
      } ?: "No upcoming cleaning found"

    val urgencyWarning =
      nextCleaning?.let {
        if ((it - now) < 6.hours) {
          "\n\n⚠️ CLEANING IS TODAY! Be extremely careful about parking here."
        } else {
          ""
        }
      } ?: ""

    val reminderLines =
      if (remindersSet.isEmpty()) {
        "No reminders set (cleaning too soon or no reminders configured)."
      } else {
        "Reminders set for:\n" + remindersSet.joinToString("\n")
      }

    val bigText = "Next cleaning: $nextCleaningText$urgencyWarning\n\n$reminderLines"

    notificationManager.showSpotFoundNotification(
      locationName = locationName,
      nextCleaningText = nextCleaningText,
      bigText = bigText,
    )
  }

  companion object {
    private const val MAX_REMINDERS = 100
    private const val ALARM_REQUEST_CODE_BASE = 1000

    val REMINDER_MINUTES = stringSetPreferencesKey("reminder_minutes")
  }
}
