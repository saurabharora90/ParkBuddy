package dev.bongballe.parkbuddy.model

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * Represents a parkable street segment.
 *
 * Each parking spot represents ONE side of a street block. The geometry is a polyline along that
 * side, and sweeping schedules are specific to that side.
 *
 * ## Timeline Architecture
 * The [timeline] contains pre-resolved [ParkingInterval]s computed during data sync. Overlapping
 * regulations and meter schedules are flattened using priority-based resolution: FORBIDDEN >
 * RESTRICTED > METERED > LIMITED > OPEN.
 *
 * Sweeping schedules are kept separate in [sweepingSchedules] because they have week-of-month
 * semantics (week1-week5) that a weekly timeline cannot represent.
 *
 * @property objectId Unique identifier for this parking spot
 * @property geometry Polyline coordinates representing this street segment (one side of street)
 * @property streetName Street name (e.g., "Main St"), may be null if unavailable
 * @property blockLimits Cross streets defining the block (e.g., "1st Ave - 2nd Ave")
 * @property neighborhood Neighborhood or district name
 * @property rppAreas Residential Parking Permit zone identifiers, empty if not in a permit zone
 * @property timeline Pre-resolved weekly parking rules. Gaps between intervals are implicitly OPEN.
 * @property sweepingCnn Street segment identifier used for matching sweeping schedules
 * @property sweepingSide Which side of the street (LEFT/RIGHT) this spot is on
 * @property sweepingSchedules All street cleaning schedules for this side of the street
 */
@Serializable
data class ParkingSpot(
  val objectId: String,
  val geometry: Geometry,
  val streetName: String?,
  val blockLimits: String?,
  val neighborhood: String?,
  val rppAreas: List<String>,
  val timeline: List<ParkingInterval> = emptyList(),
  val sweepingCnn: String?,
  val sweepingSide: StreetSide?,
  val sweepingSchedules: List<SweepingSchedule>,
) {
  /** Returns the next street cleaning start time, or null if no cleaning is scheduled. */
  fun nextCleaning(now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): Instant? =
    sweepingSchedules.mapNotNull { it.nextOccurrence(now, zone) }.minOrNull()

  /** Whether regular passenger vehicles can park here (derived from timeline). */
  val isParkable: Boolean
    get() =
      timeline.isEmpty() ||
        timeline.any {
          it.type is IntervalType.Open ||
            it.type is IntervalType.Limited ||
            it.type is IntervalType.Metered
        }

  /** Whether this spot has any metered intervals (user needs to pay). */
  val hasMeters: Boolean
    get() = timeline.any { it.type is IntervalType.Metered }

  /** Whether this spot is primarily for commercial vehicles (Yellow/Red meter zones). */
  val isCommercial: Boolean
    get() {
      val restricted = timeline.filter { it.type is IntervalType.Restricted }
      val nonRestricted =
        timeline.filter {
          it.type !is IntervalType.Restricted && it.type !is IntervalType.Forbidden
        }
      // Commercial if all non-forbidden intervals are restricted, or restricted covers more time
      return restricted.isNotEmpty() && nonRestricted.isEmpty()
    }
}

/**
 * Represents a street sweeping schedule for a specific day of the week.
 *
 * Street sweeping typically operates on a week-of-month basis. For example, a street might be swept
 * on "1st and 3rd Tuesdays" (week1=true, week3=true) or "every Monday" (all weeks true).
 *
 * A single parking spot may have multiple [SweepingSchedule] entries if it's swept on different
 * days (e.g., one for Tuesday, one for Friday).
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
@Serializable
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
  fun nextOccurrence(now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): Instant? {
    val targetDayOfWeek = weekday.toDayOfWeek() ?: return null

    var candidate = now.toLocalDateTime(zone).date
    repeat(52 * 7) {
      if (candidate.dayOfWeek == targetDayOfWeek) {
        val weekOfMonth = getWeekOfMonth(candidate)
        if (isWeekActive(weekOfMonth)) {
          val cleaningStart = LocalDateTime(candidate, LocalTime(fromHour, 0)).toInstant(zone)
          val cleaningEnd = LocalDateTime(candidate, LocalTime(toHour, 0)).toInstant(zone)

          if (cleaningEnd > now) {
            return cleaningStart
          }
        }
      }
      candidate = candidate.plus(1, DateTimeUnit.DAY)
    }
    return null
  }

  fun isWithinWindow(time: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
    val targetDayOfWeek = weekday.toDayOfWeek() ?: return false
    val localDateTime = time.toLocalDateTime(zone)

    if (localDateTime.dayOfWeek != targetDayOfWeek) return false
    if (!isWeekActive(getWeekOfMonth(localDateTime.date))) return false

    val currentHour = localDateTime.hour
    return if (fromHour <= toHour) {
      currentHour in fromHour until toHour
    } else {
      // Over-night window (rare for street cleaning but possible)
      currentHour >= fromHour || currentHour < toHour
    }
  }

  private fun isWeekActive(weekOfMonth: Int): Boolean =
    when (weekOfMonth) {
      1 -> week1
      2 -> week2
      3 -> week3
      4 -> week4
      5 -> week5
      else -> false
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
