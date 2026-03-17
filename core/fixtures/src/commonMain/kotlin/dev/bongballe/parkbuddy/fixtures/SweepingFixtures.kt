package dev.bongballe.parkbuddy.fixtures

import androidx.annotation.VisibleForTesting
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.bongballe.parkbuddy.model.Weekday

/** Creates a [SweepingSchedule] with all weeks enabled by default. */
@VisibleForTesting
fun createSweepingSchedule(
  weekday: Weekday,
  fromHour: Int,
  toHour: Int,
  week1: Boolean = true,
  week2: Boolean = true,
  week3: Boolean = true,
  week4: Boolean = true,
  week5: Boolean = true,
  holidays: Boolean = false,
) = SweepingSchedule(weekday, fromHour, toHour, week1, week2, week3, week4, week5, holidays)
