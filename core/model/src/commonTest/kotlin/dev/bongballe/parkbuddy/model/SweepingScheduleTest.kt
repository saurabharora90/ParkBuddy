package dev.bongballe.parkbuddy.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class SweepingScheduleTest {
  private val zone = TimeZone.of("America/Los_Angeles")

  @Test
  fun `nextOccurrence finds next occurrence for every week`() {
    val schedule =
      SweepingSchedule(
        weekday = Weekday.Mon,
        fromHour = 8,
        toHour = 10,
        week1 = true,
        week2 = true,
        week3 = true,
        week4 = true,
        week5 = true,
        holidays = false,
      )

    // Sunday, Feb 22, 2026
    val now = LocalDateTime(2026, 2, 22, 10, 0).toInstant(zone)
    val next = schedule.nextOccurrence(now, zone)

    assertNotNull(next)
    val nextLocal = next.toLocalDateTime(zone)
    assertEquals(2026, nextLocal.year)
    assertEquals(2, nextLocal.monthNumber)
    assertEquals(23, nextLocal.dayOfMonth) // Monday
    assertEquals(8, nextLocal.hour)
  }

  @Test
  fun `nextOccurrence skips weeks that are not enabled`() {
    val schedule =
      SweepingSchedule(
        weekday = Weekday.Mon,
        fromHour = 8,
        toHour = 10,
        week1 = false,
        week2 = true, // 2nd Monday: Feb 9
        week3 = false,
        week4 = true, // 4th Monday: Feb 23
        week5 = false,
        holidays = false,
      )

    // Sunday, Feb 8, 2026
    val now = LocalDateTime(2026, 2, 8, 10, 0).toInstant(zone)
    val next = schedule.nextOccurrence(now, zone)

    assertNotNull(next)
    val nextLocal = next.toLocalDateTime(zone)
    assertEquals(9, nextLocal.dayOfMonth) // 2nd Monday

    // Monday, Feb 9, 2026 at 11 AM (after cleaning)
    val later = LocalDateTime(2026, 2, 9, 11, 0).toInstant(zone)
    val nextLater = schedule.nextOccurrence(later, zone)

    assertNotNull(nextLater)
    val nextLaterLocal = nextLater.toLocalDateTime(zone)
    assertEquals(23, nextLaterLocal.dayOfMonth) // 4th Monday
  }

  @Test
  fun `reproduction - nextOccurrence skips current window`() {
    val schedule =
      SweepingSchedule(
        weekday = Weekday.Mon,
        fromHour = 8,
        toHour = 10,
        week1 = true,
        week2 = true,
        week3 = true,
        week4 = true,
        week5 = true,
        holidays = false,
      )

    // Monday, Feb 23, 2026 at 9:00 AM (During cleaning)
    val now = LocalDateTime(2026, 2, 23, 9, 0).toInstant(zone)
    val next = schedule.nextOccurrence(now, zone)

    assertNotNull(next)
    val nextLocal = next.toLocalDateTime(zone)
    // FAILURE: It currently returns the NEXT week because it only looks for cleaningStart > now
    // We want to know if we are IN a window.
    assertEquals(23, nextLocal.dayOfMonth, "Should return current day if we are within the window")
  }
}
