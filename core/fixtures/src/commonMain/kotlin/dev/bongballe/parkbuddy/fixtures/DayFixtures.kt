package dev.bongballe.parkbuddy.fixtures

import androidx.annotation.VisibleForTesting
import dev.bongballe.parkbuddy.model.Weekday
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.THURSDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY

@VisibleForTesting
val WEEKDAYS: Set<DayOfWeek> = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)

@VisibleForTesting val WEEKDAYS_PLUS_SAT: Set<DayOfWeek> = WEEKDAYS + SATURDAY

@VisibleForTesting val ALL_DAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet()

@VisibleForTesting
fun DayOfWeek.toWeekday(): Weekday =
  when (this) {
    MONDAY -> Weekday.Mon
    TUESDAY -> Weekday.Tues
    WEDNESDAY -> Weekday.Wed
    THURSDAY -> Weekday.Thu
    FRIDAY -> Weekday.Fri
    SATURDAY -> Weekday.Sat
    DayOfWeek.SUNDAY -> Weekday.Sun
  }
