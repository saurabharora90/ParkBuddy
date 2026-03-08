package dev.bongballe.parkbuddy.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class MeterScheduleTest {
  private val zone = TimeZone.of("America/Los_Angeles")

  private val weekdayMeter = MeterSchedule(
    days = setOf(
      DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
      DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    ),
    startTime = LocalTime(9, 0),
    endTime = LocalTime(18, 0),
    timeLimitMinutes = 60,
  )

  private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
    LocalDateTime(year, month, day, hour, minute).toInstant(zone)

  @Test
  fun `isWithinWindow returns true during active weekday hours`() {
    // Monday 10 AM
    val time = instant(2026, 3, 2, 10, 0)
    assertTrue(weekdayMeter.isWithinWindow(time, zone))
  }

  @Test
  fun `isWithinWindow returns true at exact start time`() {
    // Monday 9 AM
    val time = instant(2026, 3, 2, 9, 0)
    assertTrue(weekdayMeter.isWithinWindow(time, zone))
  }

  @Test
  fun `isWithinWindow returns true at exact end time`() {
    // Monday 6 PM
    val time = instant(2026, 3, 2, 18, 0)
    assertTrue(weekdayMeter.isWithinWindow(time, zone))
  }

  @Test
  fun `isWithinWindow returns false before start time`() {
    // Monday 8:59 AM
    val time = instant(2026, 3, 2, 8, 59)
    assertFalse(weekdayMeter.isWithinWindow(time, zone))
  }

  @Test
  fun `isWithinWindow returns false after end time`() {
    // Monday 6:01 PM
    val time = instant(2026, 3, 2, 18, 1)
    assertFalse(weekdayMeter.isWithinWindow(time, zone))
  }

  @Test
  fun `isWithinWindow returns false on weekend for weekday schedule`() {
    // Saturday 12 PM
    val time = instant(2026, 3, 7, 12, 0)
    assertFalse(weekdayMeter.isWithinWindow(time, zone))
  }

  @Test
  fun `isWithinWindow returns false on Sunday for weekday schedule`() {
    // Sunday 12 PM
    val time = instant(2026, 3, 8, 12, 0)
    assertFalse(weekdayMeter.isWithinWindow(time, zone))
  }

  @Test
  fun `isWithinWindow handles overnight window`() {
    val overnightMeter = MeterSchedule(
      days = setOf(DayOfWeek.FRIDAY),
      startTime = LocalTime(22, 0),
      endTime = LocalTime(6, 0),
      timeLimitMinutes = 0,
      isTowZone = true,
    )

    // Friday 11 PM
    assertTrue(overnightMeter.isWithinWindow(instant(2026, 3, 6, 23, 0), zone))
    // Friday 10 PM (exact start)
    assertTrue(overnightMeter.isWithinWindow(instant(2026, 3, 6, 22, 0), zone))
    // Friday 3 AM (early morning, still within overnight window)
    assertTrue(overnightMeter.isWithinWindow(instant(2026, 3, 6, 3, 0), zone))
    // Friday 7 AM (after end)
    assertFalse(overnightMeter.isWithinWindow(instant(2026, 3, 6, 7, 0), zone))
    // Friday 9 PM (before start)
    assertFalse(overnightMeter.isWithinWindow(instant(2026, 3, 6, 21, 0), zone))
  }

  @Test
  fun `isWithinWindow with empty days matches any day`() {
    val anyDayMeter = MeterSchedule(
      days = emptySet(),
      startTime = LocalTime(8, 0),
      endTime = LocalTime(20, 0),
      timeLimitMinutes = 120,
    )

    // Saturday 12 PM
    assertTrue(anyDayMeter.isWithinWindow(instant(2026, 3, 7, 12, 0), zone))
    // Sunday 12 PM
    assertTrue(anyDayMeter.isWithinWindow(instant(2026, 3, 8, 12, 0), zone))
    // Monday 12 PM
    assertTrue(anyDayMeter.isWithinWindow(instant(2026, 3, 2, 12, 0), zone))
  }
}
