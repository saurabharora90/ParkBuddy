package dev.bongballe.parkbuddy.data.repository.utils

import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.TimedRestriction
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
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
    val effectiveStart = calculateEffectiveStart(parkedAt, restriction, zone)
    val expiry = effectiveStart?.plus(restriction.limitHours.hours)

    return when {
      effectiveStart == null -> ParkingRestrictionState.Unrestricted(nextCleaning)
      effectiveStart > currentTime -> ParkingRestrictionState.PendingTimed(
        startsAt = effectiveStart,
        expiry = expiry!!,
        nextCleaning = nextCleaning,
      )

      else -> ParkingRestrictionState.ActiveTimed(
        expiry = expiry!!,
        nextCleaning = nextCleaning,
      )
    }
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
