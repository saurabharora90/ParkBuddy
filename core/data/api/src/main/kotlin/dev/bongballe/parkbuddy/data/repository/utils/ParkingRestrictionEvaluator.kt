package dev.bongballe.parkbuddy.data.repository.utils

import dev.bongballe.parkbuddy.model.ParkedLocation
import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ParkingSpot
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
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
    currentTime: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault()
  ): ParkingRestrictionState {
    // 1. Determine next cleaning
    val nextCleaning = spot.sweepingSchedules
      .mapNotNull { it.nextOccurrence(currentTime, zone) }
      .minByOrNull { it }

    // 2. Check if user has permit
    if (spot.rppArea != null && spot.rppArea == userPermitZone) {
      return ParkingRestrictionState.PermitSafe(nextCleaning)
    }

    // 3. Check for time limits
    val timeLimit = spot.timeLimitHours
    if (timeLimit == null) {
      return ParkingRestrictionState.Unrestricted(nextCleaning)
    }

    // 4. Evaluate timed restriction
    val effectiveStart = calculateEffectiveStart(parkedAt, spot, zone)
    val expiry = effectiveStart?.plus(timeLimit.hours)

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
    spot: ParkingSpot,
    zone: TimeZone
  ): Instant? {
    val schedule = spot.enforcementSchedule

    // If parked during a window
    if (schedule.isWithinWindow(parkedAt, zone)) {
      return parkedAt
    }

    // Check if parked before today's window
    val localDateTime = parkedAt.toLocalDateTime(zone)
    val start = schedule.startTime
    if (start != null) {
      val todayStart = LocalDateTime(localDateTime.date, start).toInstant(zone)
      if (todayStart > parkedAt && schedule.isWithinWindow(todayStart, zone)) {
        return todayStart
      }
    }

    // Find next available window
    var candidateDate = localDateTime.date.plus(1, DateTimeUnit.DAY)
    repeat(7) {
      val candidateStart = start ?: LocalTime(0, 0)
      val candidateInstant = LocalDateTime(candidateDate, candidateStart).toInstant(zone)
      if (schedule.isWithinWindow(candidateInstant, zone)) {
        return candidateInstant
      }
      candidateDate = candidateDate.plus(1, DateTimeUnit.DAY)
    }

    return null
  }
}
