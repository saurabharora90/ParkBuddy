package dev.bongballe.parkbuddy.data.repository.utils

import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ParkingSpot
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Evaluates the current parking restriction for a spot using the pre-resolved timeline.
 *
 * Evaluation order:
 * 1. Sweeping (checked separately because of week-of-month semantics).
 * 2. Active timeline interval (highest-priority rule that covers `currentTime`).
 * 3. Permit exemption on the active interval.
 * 4. Upcoming intervals (PendingTimed or upcoming Forbidden warning).
 * 5. Fallback to Unrestricted if nothing applies.
 */
object ParkingRestrictionEvaluator {

  fun evaluate(
    spot: ParkingSpot,
    userPermitZone: String?,
    parkedAt: Instant,
    currentTime: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
  ): ParkingRestrictionState {
    val nextCleaning = spot.nextCleaning(currentTime, zone)

    // 1. Street cleaning is always checked first (week-of-month semantics live on SweepingSchedule)
    val activeCleaning = spot.sweepingSchedules.firstOrNull { it.isWithinWindow(currentTime, zone) }
    if (activeCleaning != null) {
      val today = currentTime.toLocalDateTime(zone).date
      val cleaningEnd = LocalDateTime(today, LocalTime(activeCleaning.toHour, 0)).toInstant(zone)
      return ParkingRestrictionState.CleaningActive(
        cleaningEnd = cleaningEnd,
        nextCleaning = nextCleaning,
      )
    }

    // 2. Find the active interval from the pre-resolved timeline
    val activeInterval = spot.timeline.firstOrNull { it.isActiveAt(currentTime, zone) }

    // No active interval: check what's coming up next.
    // This handles the gap between enforcement windows (e.g., parking at 7pm when rules are
    // 8am-6pm).
    if (activeInterval == null) {
      val nextInterval = findNextInterval(spot.timeline, currentTime, zone)
      if (nextInterval != null) {
        val (startInstant, interval) = nextInterval
        return when (val type = interval.type) {
          // Upcoming Forbidden/Restricted: warn the user about impending no-parking
          is IntervalType.Forbidden ->
            ParkingRestrictionState.Forbidden(
              "${type.reason} starts at ${formatHourMinute(interval.startTime)}",
              nextCleaning,
            )

          is IntervalType.Restricted ->
            ParkingRestrictionState.Forbidden(
              "${type.reason} starts at ${formatHourMinute(interval.startTime)}",
              nextCleaning,
            )
          // Upcoming timed enforcement: PendingTimed
          is IntervalType.Metered,
          is IntervalType.Limited -> {
            val limitMinutes = interval.timeLimitMinutes
            if (limitMinutes != null) {
              val expiry = startInstant + limitMinutes.minutes
              ParkingRestrictionState.PendingTimed(
                startsAt = startInstant,
                expiry = expiry,
                paymentRequired = interval.requiresPayment,
                nextCleaning = nextCleaning,
              )
            } else {
              ParkingRestrictionState.Unrestricted(nextCleaning)
            }
          }

          is IntervalType.Open -> ParkingRestrictionState.Unrestricted(nextCleaning)
        }
      }
      return ParkingRestrictionState.Unrestricted(nextCleaning)
    }

    // 3. Permit exemption: if the user holds a permit for one of the exempt zones, they're safe
    if (userPermitZone != null && userPermitZone in activeInterval.exemptPermitZones) {
      return ParkingRestrictionState.PermitSafe(nextCleaning)
    }

    // 4. Map the interval type to the appropriate restriction state
    return when (val type = activeInterval.type) {
      is IntervalType.Open -> ParkingRestrictionState.Unrestricted(nextCleaning)

      is IntervalType.Forbidden -> ParkingRestrictionState.Forbidden(type.reason, nextCleaning)

      is IntervalType.Restricted -> ParkingRestrictionState.Forbidden(type.reason, nextCleaning)

      is IntervalType.Metered,
      is IntervalType.Limited ->
        evaluateTimedInterval(
          activeInterval,
          spot.timeline,
          spot.sweepingSchedules.mapNotNull { it.nextOccurrence(currentTime, zone) }.minOrNull(),
          parkedAt,
          currentTime,
          zone,
          nextCleaning,
        )
    }
  }

  /**
   * Computes [ActiveTimed] or [PendingTimed] for a metered/limited interval.
   *
   * "True limit" calculation: the effective deadline is the earliest of:
   * - parkedAt + timeLimit (the regulation's time limit)
   * - next Forbidden interval start on the applicable day
   * - next street cleaning start
   *
   * This prevents the user from thinking they have 2 hours when a tow-away window starts in 1.
   */
  private fun evaluateTimedInterval(
    interval: ParkingInterval,
    timeline: List<ParkingInterval>,
    nextCleaningStart: Instant?,
    parkedAt: Instant,
    currentTime: Instant,
    zone: TimeZone,
    nextCleaning: Instant?,
  ): ParkingRestrictionState {
    val local = currentTime.toLocalDateTime(zone)
    val today = local.date
    val windowStart = LocalDateTime(today, interval.startTime).toInstant(zone)

    // The enforcement clock starts when the user parked or when the window opened, whichever is
    // later
    val effectiveStart = maxOf(parkedAt, windowStart)
    val limitMinutes = interval.timeLimitMinutes

    if (limitMinutes == null) {
      // No time limit (e.g. metered with 0-minute limit means "pay, no cap").
      val windowEnd = LocalDateTime(today, interval.endTime).toInstant(zone)
      return ParkingRestrictionState.ActiveTimed(
        expiry = windowEnd,
        paymentRequired = interval.requiresPayment,
        nextCleaning = nextCleaning,
      )
    }

    val limitExpiry = effectiveStart + limitMinutes.minutes

    // Clamp to next Forbidden interval on the correct day
    val trueExpiry = clampToNextForbidden(limitExpiry, timeline, currentTime, zone)

    // Also clamp to next cleaning (sweeping creates a hard deadline too)
    val finalExpiry =
      if (
        nextCleaningStart != null &&
          nextCleaningStart > currentTime &&
          nextCleaningStart < trueExpiry
      ) {
        nextCleaningStart
      } else {
        trueExpiry
      }

    return if (effectiveStart > currentTime) {
      ParkingRestrictionState.PendingTimed(
        startsAt = effectiveStart,
        expiry = finalExpiry,
        paymentRequired = interval.requiresPayment,
        nextCleaning = nextCleaning,
      )
    } else {
      ParkingRestrictionState.ActiveTimed(
        expiry = finalExpiry,
        paymentRequired = interval.requiresPayment,
        nextCleaning = nextCleaning,
      )
    }
  }

  /**
   * Finds the next timeline interval that starts after [currentTime]. Scans up to 7 days ahead.
   *
   * Returns the earliest upcoming interval across all days, correctly checking each interval's
   * applicable days.
   */
  private fun findNextInterval(
    timeline: List<ParkingInterval>,
    currentTime: Instant,
    zone: TimeZone,
  ): Pair<Instant, ParkingInterval>? {
    val local = currentTime.toLocalDateTime(zone)
    var candidateDate = local.date
    var best: Pair<Instant, ParkingInterval>? = null

    repeat(7) {
      for (interval in timeline) {
        if (candidateDate.dayOfWeek !in interval.days) continue
        val startInstant = LocalDateTime(candidateDate, interval.startTime).toInstant(zone)
        if (startInstant > currentTime) {
          if (best == null || startInstant < best.first) {
            best = startInstant to interval
          }
        }
      }
      // If we found something on this day, return it (earlier day always wins over later day)
      if (best != null) return best
      candidateDate = candidateDate.plus(1, DateTimeUnit.DAY)
    }
    return best
  }

  /**
   * If a FORBIDDEN interval starts before [limitExpiry], returns the earlier of the two so the user
   * is warned before a tow-away or no-parking window begins.
   *
   * Checks both today and tomorrow to handle overnight scenarios (e.g., parked at 23:30 with a
   * 2-hour limit, tow zone starts at 00:00 tomorrow).
   */
  private fun clampToNextForbidden(
    limitExpiry: Instant,
    timeline: List<ParkingInterval>,
    currentTime: Instant,
    zone: TimeZone,
  ): Instant {
    val local = currentTime.toLocalDateTime(zone)
    val today = local.date
    val tomorrow = today.plus(1, DateTimeUnit.DAY)

    val candidates = mutableListOf<Instant>()

    for (forbidden in timeline) {
      if (forbidden.type !is IntervalType.Forbidden) continue

      // Check today
      if (today.dayOfWeek in forbidden.days) {
        val start = LocalDateTime(today, forbidden.startTime).toInstant(zone)
        if (start > currentTime && start < limitExpiry) candidates.add(start)
      }

      // Check tomorrow (handles overnight limit windows crossing midnight)
      if (tomorrow.dayOfWeek in forbidden.days) {
        val start = LocalDateTime(tomorrow, forbidden.startTime).toInstant(zone)
        if (start > currentTime && start < limitExpiry) candidates.add(start)
      }
    }

    return candidates.minOrNull() ?: limitExpiry
  }

  private fun formatHourMinute(time: LocalTime): String {
    val hour = time.hour
    val ampm = if (hour < 12) "AM" else "PM"
    val displayHour =
      when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
      }
    return if (time.minute == 0) "$displayHour $ampm"
    else "$displayHour:${time.minute.toString().padStart(2, '0')} $ampm"
  }
}
