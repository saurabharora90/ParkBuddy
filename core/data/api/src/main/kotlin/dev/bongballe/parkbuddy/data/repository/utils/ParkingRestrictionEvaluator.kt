package dev.bongballe.parkbuddy.data.repository.utils

import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.TimedRestriction
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

object ParkingRestrictionEvaluator {

  fun evaluate(
    spot: ParkingSpot,
    userPermitZone: String?,
    parkedAt: Instant,
    currentTime: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault()
  ): ParkingRestrictionState {
    // 1. Determine next cleaning
    val nextCleaning = spot.nextCleaning(currentTime, zone)

    // 2. Check if user has permit
    if (spot.rppArea != null && spot.rppArea == userPermitZone) {
      return ParkingRestrictionState.PermitSafe(nextCleaning)
    }

    // 3. Check for time limits
    val restriction = spot.timedRestriction
      ?: return ParkingRestrictionState.Unrestricted(nextCleaning)

    // 4. Evaluate timed restriction
    //
    // Find the enforcement window where the time limit can actually be violated.
    // If the remaining time in the current window is shorter than the limit,
    // the user can't exceed it, so skip to the next full window.
    //
    //   Example: 2hr limit, enforcement 8am-10pm. Park at 9:45 PM.
    //   Remaining today = 15 min < 2hr, so skip to tomorrow 8 AM.
    //   Result: PendingTimed(startsAt=tomorrow 8AM, expiry=tomorrow 10AM)
    val effectiveStart = findEnforceableWindow(parkedAt, restriction, zone)
      ?: return ParkingRestrictionState.Unrestricted(nextCleaning)
    val expiry = effectiveStart.plus(restriction.limitHours.hours)

    return when {
      effectiveStart > currentTime -> ParkingRestrictionState.PendingTimed(
        startsAt = effectiveStart,
        expiry = expiry,
        nextCleaning = nextCleaning,
      )

      else -> ParkingRestrictionState.ActiveTimed(
        expiry = expiry,
        nextCleaning = nextCleaning,
      )
    }
  }

  /**
   * Finds the first enforcement window where the user could actually violate
   * the time limit. If parked during a window but the remaining time in that
   * window is less than the limit, it's impossible to exceed it, so we skip
   * to the next full window.
   */
  private fun findEnforceableWindow(
    parkedAt: Instant,
    restriction: TimedRestriction,
    zone: TimeZone
  ): Instant? {
    val effectiveStart = calculateEffectiveStart(parkedAt, restriction, zone) ?: return null

    val endTime = restriction.endTime ?: return effectiveStart

    val startDate = effectiveStart.toLocalDateTime(zone).date
    val enforcementEnd = LocalDateTime(startDate, endTime).toInstant(zone)
    val remainingInWindow = enforcementEnd - effectiveStart

    if (remainingInWindow >= restriction.limitHours.hours) return effectiveStart

    // Not enough time left in this window. Find the next full window.
    // Add 1s to move past the inclusive end boundary of isWithinWindow.
    return calculateEffectiveStart(enforcementEnd + 1.seconds, restriction, zone)
  }

  private fun calculateEffectiveStart(
    parkedAt: Instant,
    restriction: TimedRestriction,
    zone: TimeZone
  ): Instant? {
    // If parked during a window
    if (restriction.isWithinWindow(parkedAt, zone)) {
      return parkedAt
    }

    // Check if parked before today's window
    val localDateTime = parkedAt.toLocalDateTime(zone)
    val start = restriction.startTime
    if (start != null) {
      val todayStart = LocalDateTime(localDateTime.date, start).toInstant(zone)
      if (todayStart > parkedAt && restriction.isWithinWindow(todayStart, zone)) {
        return todayStart
      }
    }

    // Find next available window
    var candidateDate = localDateTime.date.plus(1, DateTimeUnit.DAY)
    repeat(7) {
      val candidateStart = start ?: LocalTime(0, 0)
      val candidateInstant = LocalDateTime(candidateDate, candidateStart).toInstant(zone)
      if (restriction.isWithinWindow(candidateInstant, zone)) {
        return candidateInstant
      }
      candidateDate = candidateDate.plus(1, DateTimeUnit.DAY)
    }

    return null
  }
}
