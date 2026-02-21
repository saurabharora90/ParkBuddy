package dev.bongballe.parkbuddy.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Structured enforcement rules for a parking spot.
 */
data class EnforcementSchedule(
  val days: Set<DayOfWeek>,
  val startTime: LocalTime?,
  val endTime: LocalTime?
) {
  fun isWithinWindow(time: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
    val localDateTime = time.toLocalDateTime(zone)
    if (days.isNotEmpty() && localDateTime.dayOfWeek !in days) return false
    
    val currentTime = localDateTime.time
    val start = startTime ?: LocalTime(0, 0)
    val end = endTime ?: LocalTime(23, 59)
    
    return if (start <= end) {
      currentTime in start..end
    } else {
      // Over-night window (e.g., 10 PM to 6 AM)
      currentTime >= start || currentTime <= end
    }
  }
}

/**
 * Represents a parkable street segment.
 *
 * Each parking spot represents ONE side of a street block. The geometry is a polyline
 * along that side, and sweeping schedules are specific to that side.
 *
 * @property objectId Unique identifier for this parking spot
 * @property geometry Polyline coordinates representing this street segment (one side of street)
 * @property streetName Street name (e.g., "Main St"), may be null if unavailable
 * @property blockLimits Cross streets defining the block (e.g., "1st Ave - 2nd Ave")
 * @property neighborhood Neighborhood or district name
 * @property regulation Type of parking allowed (time-limited, permit, etc.)
 * @property rppArea Residential Parking Permit zone identifier, null if not in a permit zone
 * @property timeLimitHours Maximum parking duration in hours, null if unlimited
 * @property enforcementSchedule Structured enforcement rules
 * @property sweepingCnn Street segment identifier used for matching sweeping schedules
 * @property sweepingSide Which side of the street (LEFT/RIGHT) this spot is on
 * @property sweepingSchedules All street cleaning schedules for this side of the street.
 */
data class ParkingSpot(
  val objectId: String,
  val geometry: Geometry,
  val streetName: String?,
  val blockLimits: String?,
  val neighborhood: String?,
  val regulation: ParkingRegulation,
  val rppArea: String?,
  val timeLimitHours: Int?,
  val enforcementSchedule: EnforcementSchedule,
  val sweepingCnn: String?,
  val sweepingSide: StreetSide?,
  val sweepingSchedules: List<SweepingSchedule>,
)

/**
 * Represents a street sweeping schedule for a specific day of the week.
 *
 * Street sweeping typically operates on a week-of-month basis. For example, a street might be
 * swept on "1st and 3rd Tuesdays" (week1=true, week3=true) or "every Monday" (all weeks true).
 *
 * A single parking spot may have multiple [SweepingSchedule] entries if it's swept on
 * different days (e.g., one for Tuesday, one for Friday).
 *
 * @property weekday Day of the week when sweeping occurs
 * @property fromHour Start hour in 24h format (e.g., 8 for 8:00 AM)
 * @property toHour End hour in 24h format (e.g., 10 for 10:00 AM)
 * @property week1 Whether sweeping occurs on the 1st occurrence of [weekday] in the month
 * @property week2 Whether sweeping occurs on the 2nd occurrence of [weekday] in the month
 * @property week3 Whether sweeping occurs on the 3rd occurrence of [weekday] in the month
 * @property week4 Whether sweeping occurs on the 4th occurrence of [weekday] in the month
 * @property week5 Whether sweeping occurs on the 5th occurrence of [weekday] in the month (rare)
 * @property holidays Whether sweeping occurs on holidays
 */
data class SweepingSchedule(
  val weekday: Weekday,
  val fromHour: Int,
  val toHour: Int,
  val week1: Boolean,
  val week2: Boolean,
  val week3: Boolean,
  val week4: Boolean,
  val week5: Boolean,
  val holidays: Boolean,
) {
  fun nextOccurrence(
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
  ): Instant? {
    val targetDayOfWeek = weekday.toDayOfWeek() ?: return null

    var candidate = now.toLocalDateTime(zone).date
    repeat(52 * 7) {
      if (candidate.dayOfWeek == targetDayOfWeek) {
        val weekOfMonth = getWeekOfMonth(candidate)
        val isValidWeek = when (weekOfMonth) {
          1 -> week1
          2 -> week2
          3 -> week3
          4 -> week4
          5 -> week5
          else -> false
        }
        if (isValidWeek) {
          val cleaningStart = LocalDateTime(candidate, LocalTime(fromHour, 0))
            .toInstant(zone)
          if (cleaningStart > now) {
            return cleaningStart
          }
        }
      }
      candidate = candidate.plus(1, DateTimeUnit.DAY)
    }
    return null
  }

  private fun getWeekOfMonth(date: LocalDate): Int {
    val firstOfMonth = LocalDate(date.year, date.month, 1)
    var firstTargetDay = firstOfMonth
    while (firstTargetDay.dayOfWeek != date.dayOfWeek) {
      firstTargetDay = firstTargetDay.plus(1, DateTimeUnit.DAY)
    }
    return ((date.day - firstTargetDay.day) / 7) + 1
  }
}
