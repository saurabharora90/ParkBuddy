package dev.bongballe.parkbuddy.data.repository.utils

import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ProhibitionReason
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
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
    if (activeInterval == null) {
      val nextInterval = findNextInterval(spot.timeline, currentTime, zone)
      if (nextInterval != null) {
        val (startInstant, interval) = nextInterval
        return when (val type = interval.type) {
          is IntervalType.Forbidden ->
            ParkingRestrictionState.ForbiddenUpcoming(
              reason = type.reason,
              startsAt = startInstant,
              nextCleaning = nextCleaning,
            )

          is IntervalType.Restricted ->
            ParkingRestrictionState.ForbiddenUpcoming(
              reason = type.reason,
              startsAt = startInstant,
              nextCleaning = nextCleaning,
            )

          is IntervalType.Metered,
          is IntervalType.Limited -> {
            val limitMinutes = interval.timeLimitMinutes
            if (limitMinutes != null) {
              val rawExpiry = startInstant + limitMinutes.minutes
              // Use startInstant as anchor so clamp scans the day enforcement begins, not today
              val clampedExpiry =
                clampToNextProhibited(rawExpiry, spot.timeline, startInstant, zone)
              val expiry =
                if (
                  nextCleaning != null &&
                    nextCleaning >= startInstant &&
                    nextCleaning < clampedExpiry
                )
                  nextCleaning
                else clampedExpiry
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

    // 3. Permit exemption: if the user holds a permit for one of the exempt zones, they're safe.
    // Forbidden and non-RPP restricted intervals (commercial, loading) are never permit-exempt,
    // even if exemptPermitZones is populated (defense against upstream data issues).
    val permitExemptible =
      activeInterval.type is IntervalType.Limited ||
        activeInterval.type is IntervalType.Metered ||
        (activeInterval.type is IntervalType.Restricted &&
          (activeInterval.type as IntervalType.Restricted).reason ==
            ProhibitionReason.RESIDENTIAL_PERMIT)
    if (
      permitExemptible &&
        userPermitZone != null &&
        userPermitZone in activeInterval.exemptPermitZones
    ) {
      return ParkingRestrictionState.PermitSafe(nextCleaning)
    }

    // 4. Map the interval type to the appropriate restriction state
    return when (val type = activeInterval.type) {
      is IntervalType.Open -> ParkingRestrictionState.Unrestricted(nextCleaning)

      is IntervalType.Forbidden ->
        ParkingRestrictionState.Forbidden(reason = type.reason, nextCleaning = nextCleaning)

      is IntervalType.Restricted ->
        ParkingRestrictionState.Forbidden(reason = type.reason, nextCleaning = nextCleaning)

      is IntervalType.Metered,
      is IntervalType.Limited ->
        evaluateTimedInterval(
          activeInterval,
          spot.timeline,
          parkedAt,
          currentTime,
          zone,
          nextCleaning,
        )
    }
  }

  /**
   * Computes [ParkingRestrictionState.ActiveTimed] or [ParkingRestrictionState.PendingTimed] for a
   * metered/limited interval.
   *
   * "True limit" calculation: the effective deadline is the earliest of:
   * - parkedAt + timeLimit (the regulation's time limit)
   * - next Forbidden or Restricted interval start on the applicable day
   * - next street cleaning start
   *
   * This prevents the user from thinking they have 2 hours when a tow-away window starts in 1.
   */
  private fun evaluateTimedInterval(
    interval: ParkingInterval,
    timeline: List<ParkingInterval>,
    parkedAt: Instant,
    currentTime: Instant,
    zone: TimeZone,
    nextCleaning: Instant?,
  ): ParkingRestrictionState {
    val local = currentTime.toLocalDateTime(zone)
    val today = local.date
    val windowStart = run {
      val candidate = LocalDateTime(today, interval.startTime).toInstant(zone)
      // Overnight interval (e.g. 10 PM - 6 AM): at 1 AM, the window opened yesterday
      if (candidate > currentTime && interval.startTime > interval.endTime)
        LocalDateTime(today.minus(1, DateTimeUnit.DAY), interval.startTime).toInstant(zone)
      else candidate
    }

    // The enforcement clock starts when the user parked or when the window opened, whichever is
    // later
    val effectiveStart = maxOf(parkedAt, windowStart)
    val limitMinutes = interval.timeLimitMinutes

    if (limitMinutes == null) {
      val windowEnd = run {
        val candidate = LocalDateTime(today, interval.endTime).toInstant(zone)
        // Overnight interval: at 11 PM inside a 10 PM - 6 AM window, endTime (6 AM) is tomorrow
        if (interval.startTime > interval.endTime && candidate < currentTime)
          LocalDateTime(today.plus(1, DateTimeUnit.DAY), interval.endTime).toInstant(zone)
        else candidate
      }
      return ParkingRestrictionState.ActiveTimed(
        expiry = windowEnd,
        paymentRequired = interval.requiresPayment,
        nextCleaning = nextCleaning,
      )
    }

    val limitExpiry = effectiveStart + limitMinutes.minutes

    // Clamp to next prohibited (Forbidden or Restricted) interval on the correct day
    val trueExpiry = clampToNextProhibited(limitExpiry, timeline, currentTime, zone)

    // Also clamp to next cleaning (sweeping creates a hard deadline too)
    val finalExpiry =
      if (nextCleaning != null && nextCleaning > currentTime && nextCleaning < trueExpiry) {
        nextCleaning
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
      if (best != null) return best
      candidateDate = candidateDate.plus(1, DateTimeUnit.DAY)
    }
    return best
  }

  /**
   * If a Forbidden or Restricted interval starts before [limitExpiry], returns the earlier of the
   * two so the user is warned before a tow-away, no-parking, or commercial window begins.
   *
   * Checks both today and tomorrow to handle overnight scenarios (e.g., parked at 23:30 with a
   * 2-hour limit, tow zone starts at 00:00 tomorrow).
   */
  private fun clampToNextProhibited(
    limitExpiry: Instant,
    timeline: List<ParkingInterval>,
    currentTime: Instant,
    zone: TimeZone,
  ): Instant {
    val local = currentTime.toLocalDateTime(zone)
    val today = local.date
    val tomorrow = today.plus(1, DateTimeUnit.DAY)

    val candidates = mutableListOf<Instant>()

    for (interval in timeline) {
      if (!interval.type.isProhibited) continue

      if (today.dayOfWeek in interval.days) {
        val start = LocalDateTime(today, interval.startTime).toInstant(zone)
        if (start > currentTime && start < limitExpiry) candidates.add(start)
      }

      if (tomorrow.dayOfWeek in interval.days) {
        val start = LocalDateTime(tomorrow, interval.startTime).toInstant(zone)
        if (start > currentTime && start < limitExpiry) candidates.add(start)
      }
    }

    return candidates.minOrNull() ?: limitExpiry
  }
}
