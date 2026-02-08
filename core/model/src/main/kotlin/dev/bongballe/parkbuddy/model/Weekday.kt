package dev.bongballe.parkbuddy.model

import dev.bongballe.parkbuddy.model.Weekday.Holiday
import kotlinx.datetime.DayOfWeek

/**
 * Day of week for street sweeping schedules.
 *
 * [Holiday] is a special value used when sweeping occurs on holidays regardless of day.
 */
enum class Weekday {
  Mon,
  Tues,
  Wed,
  Thu,
  Fri,
  Sat,
  Sun,
  Holiday;

  fun toDayOfWeek(): DayOfWeek? = when (this) {
    Weekday.Mon -> DayOfWeek.MONDAY
    Weekday.Tues -> DayOfWeek.TUESDAY
    Weekday.Wed -> DayOfWeek.WEDNESDAY
    Weekday.Thu -> DayOfWeek.THURSDAY
    Weekday.Fri -> DayOfWeek.FRIDAY
    Weekday.Sat -> DayOfWeek.SATURDAY
    Weekday.Sun -> DayOfWeek.SUNDAY
    Weekday.Holiday -> null
  }
}
