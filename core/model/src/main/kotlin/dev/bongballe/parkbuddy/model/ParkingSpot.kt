package dev.bongballe.parkbuddy.model

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

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
 * @property enforcementDays Days when parking rules are enforced. Format varies by data source
 *                          (e.g., "M-F", "Mon-Fri", "Weekdays"). Kept as raw string due to
 *                          format inconsistency across different cities/sources.
 * @property enforcementStart Time when enforcement begins
 * @property enforcementEnd Time when enforcement ends
 * @property sweepingCnn Street segment identifier used for matching sweeping schedules
 * @property sweepingSide Which side of the street (LEFT/RIGHT) this spot is on
 * @property sweepingSchedules All street cleaning schedules for this side of the street.
 *                            A street may have multiple cleaning times per week.
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
  val enforcementDays: String?,
  val enforcementStart: LocalTime?,
  val enforcementEnd: LocalTime?,
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
  fun nextOccurrence(now: ZonedDateTime): ZonedDateTime? {
    val targetDayOfWeek = weekday.toDayOfWeek() ?: return null

    var candidate = now.toLocalDate()
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
          val cleaningStart = candidate.atTime(fromHour, 0)
            .atZone(now.zone)
          if (cleaningStart.isAfter(now)) {
            return cleaningStart
          }
        }
      }
      candidate = candidate.plusDays(1)
    }
    return null
  }

  private fun getWeekOfMonth(date: java.time.LocalDate): Int {
    val firstOfMonth = date.withDayOfMonth(1)
    val firstTargetDay = firstOfMonth.with(java.time.temporal.TemporalAdjusters.nextOrSame(date.dayOfWeek))
    return ((date.dayOfMonth - firstTargetDay.dayOfMonth) / 7) + 1
  }

  fun formatSchedule(): String {
    val dayName = weekday.name.take(3)
    val fromTime = if (fromHour < 12) "${fromHour}am" else if (fromHour == 12) "12pm" else "${fromHour - 12}pm"
    val toTime = if (toHour < 12) "${toHour}am" else if (toHour == 12) "12pm" else "${toHour - 12}pm"
    return "$dayName $fromTime-$toTime"
  }
}

private fun Weekday.toDayOfWeek(): DayOfWeek? = when (this) {
  Weekday.Mon -> DayOfWeek.MONDAY
  Weekday.Tues -> DayOfWeek.TUESDAY
  Weekday.Wed -> DayOfWeek.WEDNESDAY
  Weekday.Thu -> DayOfWeek.THURSDAY
  Weekday.Fri -> DayOfWeek.FRIDAY
  Weekday.Sat -> DayOfWeek.SATURDAY
  Weekday.Sun -> DayOfWeek.SUNDAY
  Weekday.Holiday -> null
}
