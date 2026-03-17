package dev.bongballe.parkbuddy.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.bongballe.parkbuddy.data.repository.utils.DateTimeUtils
import dev.bongballe.parkbuddy.data.repository.utils.ParkingRestrictionEvaluator
import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ProhibitionReason
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

/**
 * Manages parking reminder scheduling and notification display.
 *
 * Reminder flow:
 * 1. User parks -> [scheduleReminders] evaluates the spot's restrictions.
 * 2. Based on the [ParkingRestrictionState], we schedule alarms (cleaning, expiry, prohibition, or
 *    all).
 * 3. A "spot found" notification summarizes the situation and lists upcoming alarms.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ReminderRepositoryImpl(
  private val dataStore: DataStore<Preferences>,
  private val alarmScheduler: AlarmScheduler,
  private val notificationManager: ReminderNotificationManager,
  private val preferencesRepository: PreferencesRepository,
  private val parkingRepository: ParkingRepository,
  private val clock: Clock = Clock.System,
) : ReminderRepository {

  override fun getReminders(): Flow<List<ReminderMinutes>> {
    return dataStore.data
      .map { pref -> pref[REMINDER_MINUTES]?.mapNotNull { it.toIntOrNull() } ?: emptySet() }
      .map { it.map { ReminderMinutes(it) } }
  }

  override suspend fun addReminder(minutesBefore: ReminderMinutes) {
    val currentReminders = getReminders().first().toMutableSet()
    currentReminders.add(minutesBefore)
    dataStore.edit { it[REMINDER_MINUTES] = currentReminders.map { it.value.toString() }.toSet() }
  }

  override suspend fun removeReminder(minutesBefore: ReminderMinutes) {
    val currentReminders = getReminders().first().toMutableSet()
    currentReminders.remove(minutesBefore)
    dataStore.edit { it[REMINDER_MINUTES] = currentReminders.map { it.value.toString() }.toSet() }
  }

  /**
   * Evaluates restrictions for [spot] and schedules the appropriate alarms.
   *
   * Alarm strategy:
   * - **CleaningActive / Forbidden now**: No alarms; the notification itself is the warning.
   * - **ActiveTimed**: Expiry reminders (15 min before + at expiry).
   * - **Unrestricted / PermitSafe**: Cleaning reminders + prohibition reminders.
   * - **PendingTimed**: Cleaning if before enforcement, plus expiry.
   */
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
      is ParkingRestrictionState.CleaningActive -> {}

      is ParkingRestrictionState.Forbidden -> {}

      is ParkingRestrictionState.ForbiddenUpcoming -> {
        alarmIndex =
          scheduleProhibitionReminder(
            alarmIndex,
            state.startsAt,
            state.reason,
            now,
            streetName,
            spot.objectId,
            alarmsScheduled,
          )
        state.nextCleaning?.let { nextCleaning ->
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

      is ParkingRestrictionState.ActiveTimed -> {
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
          scheduleEnforcementStartReminder(
            alarmIndex,
            state.startsAt,
            state.paymentRequired,
            now,
            streetName,
            spot.objectId,
            alarmsScheduled,
          )
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
        state.nextCleaning?.let { nextCleaning ->
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
      showSpotFoundNotification(spot, streetName, state, remindersSet = alarmsScheduled)
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
        if (reminderTime > now && alarmIndex < AlarmScheduler.MAX_REMINDERS) {
          alarmScheduler.setAlarm(
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
    if (reminderTime > now && alarmIndex < AlarmScheduler.MAX_REMINDERS) {
      alarmScheduler.setAlarm(
        alarmIndex++,
        reminderTime,
        streetName,
        spotId,
        "Expires soon, move your car!",
      )
      alarmsScheduled.add(formatReminderDisplay(reminderTime))
    }

    if (alarmIndex < AlarmScheduler.MAX_REMINDERS) {
      alarmScheduler.setAlarm(
        alarmIndex++,
        expiry,
        streetName,
        spotId,
        "EXPIRED, move your car now!",
      )
      alarmsScheduled.add(formatReminderDisplay(expiry))
    }
    return alarmIndex
  }

  private fun scheduleEnforcementStartReminder(
    startIndex: Int,
    startsAt: Instant,
    paymentRequired: Boolean,
    now: Instant,
    streetName: String,
    spotId: String,
    alarmsScheduled: MutableList<String>,
  ): Int {
    if (startsAt <= now) return startIndex
    var alarmIndex = startIndex

    val label = if (paymentRequired) "Metered parking" else "Time-limited parking"

    val reminderTime = startsAt - 15.minutes
    if (reminderTime > now && alarmIndex < AlarmScheduler.MAX_REMINDERS) {
      alarmScheduler.setAlarm(
        alarmIndex++,
        reminderTime,
        streetName,
        spotId,
        "$label starts soon, move or pay!",
      )
      alarmsScheduled.add(formatReminderDisplay(reminderTime))
    }

    if (alarmIndex < AlarmScheduler.MAX_REMINDERS) {
      alarmScheduler.setAlarm(alarmIndex++, startsAt, streetName, spotId, "$label is now active!")
      alarmsScheduled.add(formatReminderDisplay(startsAt))
    }

    return alarmIndex
  }

  private fun scheduleProhibitionReminder(
    startIndex: Int,
    startsAt: Instant,
    reason: ProhibitionReason,
    now: Instant,
    streetName: String,
    spotId: String,
    alarmsScheduled: MutableList<String>,
  ): Int {
    if (startsAt <= now) return startIndex
    var alarmIndex = startIndex

    val reasonText = prohibitionReasonText(reason)

    val reminderTime = startsAt - 15.minutes
    if (reminderTime > now && alarmIndex < AlarmScheduler.MAX_REMINDERS) {
      alarmScheduler.setAlarm(
        alarmIndex++,
        reminderTime,
        streetName,
        spotId,
        "$reasonText starts soon, move your car!",
      )
      alarmsScheduled.add(formatReminderDisplay(reminderTime))
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
    return "\u2022 $dayName at $displayHour:$displayMinute $amPm"
  }

  override suspend fun clearAllReminders() {
    notificationManager.cancelAll()
    alarmScheduler.cancelAll()
  }

  private fun showSpotFoundNotification(
    spot: ParkingSpot,
    streetName: String,
    state: ParkingRestrictionState,
    remindersSet: List<String>,
  ) {
    val now = clock.now()
    val zone = TimeZone.currentSystemDefault()

    val title = buildTitle(streetName, state)

    val reminderLines =
      if (remindersSet.isEmpty()) "No reminders set."
      else "Reminders set for:\n" + remindersSet.joinToString("\n")

    val (contentText, bigText) =
      when (state) {
        is ParkingRestrictionState.Forbidden -> {
          val reason = prohibitionReasonText(state.reason)
          "\u26A0\uFE0F DO NOT PARK HERE: ${reason.uppercase()}" to
            "\u26A0\uFE0F DO NOT PARK HERE!\nReason: $reason\n\n" +
              "This is a restricted zone. MOVE YOUR CAR IMMEDIATELY."
        }

        is ParkingRestrictionState.ForbiddenUpcoming -> {
          val reason = prohibitionReasonText(state.reason)
          "Restriction ahead: $reason" to "Restriction ahead: $reason\n\n$reminderLines"
        }

        is ParkingRestrictionState.CleaningActive -> {
          val cleaningEndText = formatTime(state.cleaningEnd, zone)
          "\u26A0\uFE0F STREET CLEANING IN PROGRESS!" to
            "\u26A0\uFE0F STREET CLEANING IN PROGRESS!\nEnds at $cleaningEndText. MOVE YOUR CAR NOW!"
        }

        is ParkingRestrictionState.ActiveTimed -> {
          val expiryText = "Move by ${formatTime(state.expiry, zone)}"
          val paymentWarning =
            when {
              !state.paymentRequired -> ""
              spot.hasMeters && spot.rppAreas.isNotEmpty() ->
                "\n\u26A0\uFE0F PAY AT METER: Metered spot in Zone ${spot.rppAreas.joinToString(" or ")}."
              else -> "\n\u26A0\uFE0F PAY AT METER: Standard metered parking."
            }
          val ct =
            if (state.paymentRequired) "\u26A0\uFE0F PAY AT METER. $expiryText" else expiryText
          ct to "$expiryText$paymentWarning\n\n$reminderLines"
        }

        is ParkingRestrictionState.PendingTimed -> {
          val dayName =
            if (state.startsAt.toLocalDateTime(zone).date == now.toLocalDateTime(zone).date) "today"
            else "tomorrow"
          val expiryText = "Starts $dayName. Move by ${formatTime(state.expiry, zone)}"
          val paymentWarning =
            when {
              !state.paymentRequired -> ""
              spot.hasMeters && spot.rppAreas.isNotEmpty() ->
                "\n\u26A0\uFE0F PAY AT METER: Metered spot in Zone ${spot.rppAreas.joinToString(" or ")}."
              else -> "\n\u26A0\uFE0F PAY AT METER: Standard metered parking."
            }
          val ct =
            if (state.paymentRequired) "\u26A0\uFE0F PAY AT METER. $expiryText" else expiryText
          val nextCleaning = state.nextCleaning
          if (nextCleaning != null && nextCleaning < state.startsAt) {
            val cleaningText = formattedCleaningText(nextCleaning, now, zone)
            val urgencyWarning =
              if ((nextCleaning - now) < 6.hours) "\n\u26A0\uFE0F CLEANING IS TODAY!" else ""
            ct to
              "$expiryText$paymentWarning\nNext cleaning: $cleaningText$urgencyWarning\n\n$reminderLines"
          } else {
            ct to "$expiryText$paymentWarning\n\n$reminderLines"
          }
        }

        is ParkingRestrictionState.PermitSafe,
        is ParkingRestrictionState.Unrestricted -> {
          val nextCleaning = state.nextCleaning
          val cleaningText =
            nextCleaning?.let { formattedCleaningText(it, now, zone) }
              ?: "No upcoming cleaning found"
          val urgencyWarning =
            nextCleaning
              ?.let { if ((it - now) < 6.hours) "\n\u26A0\uFE0F CLEANING IS TODAY!" else "" }
              .orEmpty()
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

  private fun buildTitle(streetName: String, state: ParkingRestrictionState): String {
    val suffix =
      when (state) {
        is ParkingRestrictionState.CleaningActive -> " \u26A0\uFE0F NO PARKING"
        is ParkingRestrictionState.Forbidden -> " \u26A0\uFE0F NO PARKING"
        is ParkingRestrictionState.ForbiddenUpcoming -> ""
        is ParkingRestrictionState.PermitSafe -> " (Permit zone)"
        is ParkingRestrictionState.ActiveTimed -> ""
        is ParkingRestrictionState.PendingTimed -> ""
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

  private fun prohibitionReasonText(reason: ProhibitionReason): String =
    when (reason) {
      ProhibitionReason.TOW_AWAY -> "Tow Away Zone"
      ProhibitionReason.NO_PARKING -> "No Parking"
      ProhibitionReason.NO_STOPPING -> "No Stopping"
      ProhibitionReason.NO_OVERNIGHT -> "No Overnight Parking"
      ProhibitionReason.STREET_CLEANING -> "Street Cleaning"
      ProhibitionReason.COMMERCIAL -> "Commercial Only"
      ProhibitionReason.LOADING_ZONE -> "Loading Zone"
      ProhibitionReason.RESIDENTIAL_PERMIT -> "Residential Permit"
    }

  companion object {
    private val REMINDER_MINUTES = stringSetPreferencesKey("reminder_minutes")
  }
}
