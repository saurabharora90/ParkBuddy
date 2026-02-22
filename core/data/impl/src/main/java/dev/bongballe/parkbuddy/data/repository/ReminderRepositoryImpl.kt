package dev.bongballe.parkbuddy.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.bongballe.parkbuddy.data.repository.utils.DateTimeUtils
import dev.bongballe.parkbuddy.data.repository.utils.ParkingRestrictionEvaluator
import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ReminderMinutes
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
  private val preferencesRepository: PreferencesRepository,
  private val parkingRepository: ParkingRepository,
  private val clock: Clock = Clock.System,
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
    val parkedLocation = preferencesRepository.parkedLocation.first() ?: return
    val userZone = parkingRepository.getUserPermitZone().first()
    val now = clock.now()

    val state =
      ParkingRestrictionEvaluator.evaluate(
        spot = spot,
        userPermitZone = userZone,
        parkedAt = parkedLocation.parkedAt,
        currentTime = now,
      )

    val streetName = spot.streetName ?: spot.neighborhood ?: "Unknown Street"
    val alarmsScheduled = mutableListOf<String>()
    var alarmIndex = 0

    when (state) {
      is ParkingRestrictionState.ActiveTimed -> {
        // Time limit is running. User must move before expiry, so only expiry reminders.
        alarmIndex =
          scheduleExpiryReminders(
            alarmIndex,
            state.expiry,
            now,
            streetName,
            spot.objectId,
            alarmsScheduled,
          )
      }

      is ParkingRestrictionState.PendingTimed -> {
        // Enforcement hasn't started yet. Schedule cleaning reminders only if
        // cleaning happens before enforcement starts (user would need to move for
        // cleaning before the time limit even kicks in). Always schedule expiry.
        val nextCleaning = state.nextCleaning
        if (nextCleaning != null && nextCleaning < state.startsAt) {
          alarmIndex =
            scheduleCleaningReminders(
              alarmIndex,
              nextCleaning,
              reminders,
              now,
              spot,
              streetName,
              alarmsScheduled,
            )
        }
        alarmIndex =
          scheduleExpiryReminders(
            alarmIndex,
            state.expiry,
            now,
            streetName,
            spot.objectId,
            alarmsScheduled,
          )
      }

      is ParkingRestrictionState.Unrestricted,
      is ParkingRestrictionState.PermitSafe -> {
        val nextCleaning = state.nextCleaning
        if (nextCleaning != null) {
          alarmIndex =
            scheduleCleaningReminders(
              alarmIndex,
              nextCleaning,
              reminders,
              now,
              spot,
              streetName,
              alarmsScheduled,
            )
        }
      }
    }

    if (showNotification) {
      showSpotFoundNotification(
        spot = spot,
        streetName = streetName,
        state = state,
        remindersSet = alarmsScheduled,
      )
    }
  }

  private fun scheduleCleaningReminders(
    startIndex: Int,
    nextCleaning: Instant,
    reminders: List<ReminderMinutes>,
    now: Instant,
    spot: ParkingSpot,
    streetName: String,
    alarmsScheduled: MutableList<String>,
  ): Int {
    var alarmIndex = startIndex
    val schedule =
      spot.sweepingSchedules.sortedBy { it.nextOccurrence(now) }.firstOrNull() ?: return alarmIndex

    val cleaningStartTime = DateTimeUtils.formatHour(schedule.fromHour)
    reminders
      .sortedBy { it.value }
      .forEach { reminder ->
        val reminderTime = nextCleaning - reminder.value.minutes
        if (reminderTime > now && alarmIndex < MAX_REMINDERS) {
          setAlarm(
            alarmIndex++,
            reminderTime,
            streetName,
            spot.objectId,
            "Cleaning starts at $cleaningStartTime",
          )
          alarmsScheduled.add(formatReminderDisplay(reminderTime))
        }
      }
    return alarmIndex
  }

  private fun scheduleExpiryReminders(
    startIndex: Int,
    expiry: Instant,
    now: Instant,
    streetName: String,
    spotId: String,
    alarmsScheduled: MutableList<String>,
  ): Int {
    if (expiry <= now) return startIndex
    var alarmIndex = startIndex

    val reminderTime = expiry - 15.minutes
    if (reminderTime > now && alarmIndex < MAX_REMINDERS) {
      setAlarm(alarmIndex++, reminderTime, streetName, spotId, "Expires soon, move your car!")
      alarmsScheduled.add(formatReminderDisplay(reminderTime))
    }

    if (alarmIndex < MAX_REMINDERS) {
      setAlarm(alarmIndex++, expiry, streetName, spotId, "EXPIRED, move your car now!")
      alarmsScheduled.add(formatReminderDisplay(expiry))
    }
    return alarmIndex
  }

  private fun formatReminderDisplay(time: Instant): String {
    val localTime = time.toLocalDateTime(TimeZone.currentSystemDefault())
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
    return "• $dayName at $displayHour:$displayMinute $amPm"
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
    spot: ParkingSpot,
    streetName: String,
    state: ParkingRestrictionState,
    remindersSet: List<String>,
  ) {
    val now = clock.now()
    val zone = TimeZone.currentSystemDefault()

    val title = buildTitle(streetName, spot, state)

    val reminderLines =
      if (remindersSet.isEmpty()) "No reminders set."
      else "Reminders set for:\n" + remindersSet.joinToString("\n")

    val (contentText, bigText) =
      when (state) {
        is ParkingRestrictionState.ActiveTimed -> {
          val expiryText = "Move by ${formatTime(state.expiry, zone)}"
          expiryText to "$expiryText\n\n$reminderLines"
        }

        is ParkingRestrictionState.PendingTimed -> {
          val dayName =
            if (state.startsAt.toLocalDateTime(zone).date == now.toLocalDateTime(zone).date) "today"
            else "tomorrow"
          val expiryText = "Starts $dayName. Move by ${formatTime(state.expiry, zone)}"

          val nextCleaning = state.nextCleaning
          if (nextCleaning != null && nextCleaning < state.startsAt) {
            val cleaningText = formattedCleaningText(nextCleaning, now, zone)
            val urgencyWarning =
              if ((nextCleaning - now) < 6.hours) "\n⚠️ CLEANING IS TODAY!" else ""
            expiryText to
              "$expiryText\nNext cleaning: $cleaningText$urgencyWarning\n\n$reminderLines"
          } else {
            expiryText to "$expiryText\n\n$reminderLines"
          }
        }

        is ParkingRestrictionState.Unrestricted,
        is ParkingRestrictionState.PermitSafe -> {
          val nextCleaning = state.nextCleaning
          val cleaningText =
            nextCleaning?.let { formattedCleaningText(it, now, zone) }
              ?: "No upcoming cleaning found"
          val urgencyWarning =
            nextCleaning?.let { if ((it - now) < 6.hours) "\n⚠️ CLEANING IS TODAY!" else "" } ?: ""
          "Next cleaning: $cleaningText" to
            "Next cleaning: $cleaningText$urgencyWarning\n\n$reminderLines"
        }
      }

    notificationManager.showSpotFoundNotification(
      title = title,
      contentText = contentText,
      bigText = bigText,
    )
  }

  private fun buildTitle(
    streetName: String,
    spot: ParkingSpot,
    state: ParkingRestrictionState,
  ): String {
    val suffix =
      when (state) {
        is ParkingRestrictionState.PermitSafe -> " (Permit zone)"
        is ParkingRestrictionState.ActiveTimed,
        is ParkingRestrictionState.PendingTimed -> {
          val hours = spot.timedRestriction?.limitHours
          if (hours != null) " (${hours}hr limit)" else ""
        }
        is ParkingRestrictionState.Unrestricted -> ""
      }
    return "$streetName$suffix"
  }

  private fun formatTime(instant: Instant, zone: TimeZone): String {
    val localTime = instant.toLocalDateTime(zone)
    val hour = localTime.hour
    val minute = localTime.minute.toString().padStart(2, '0')
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour =
      when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
      }
    return "$displayHour:$minute $amPm"
  }

  private fun formattedCleaningText(nextCleaning: Instant, now: Instant, zone: TimeZone): String {
    val local = nextCleaning.toLocalDateTime(zone)
    val day = local.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val month = local.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val date = local.day
    val hour = local.hour
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour =
      when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
      }
    val timeText = "$displayHour $amPm"

    return if ((nextCleaning - now) < 6.hours) {
      "TODAY ($day, $month $date at $timeText)"
    } else {
      "$day, $month $date at $timeText"
    }
  }

  companion object {
    private const val MAX_REMINDERS = 100
    private const val ALARM_REQUEST_CODE_BASE = 1000

    val REMINDER_MINUTES = stringSetPreferencesKey("reminder_minutes")
  }
}
