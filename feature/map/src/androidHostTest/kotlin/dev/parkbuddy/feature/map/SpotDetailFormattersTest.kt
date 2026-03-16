package dev.parkbuddy.feature.map

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DayOfWeek
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpotDetailFormattersTest {

  @Test
  fun `formatDayRange all 7 days returns Daily`() {
    assertThat(formatDayRange(DayOfWeek.entries.toSet())).isEqualTo("Daily")
  }

  @Test
  fun `formatDayRange Mon-Fri returns contiguous range`() {
    val monFri =
      setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
      )
    assertThat(formatDayRange(monFri)).isEqualTo("Mon-Fri")
  }

  @Test
  fun `formatDayRange Mon-Sat returns contiguous range`() {
    val monSat =
      setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
      )
    assertThat(formatDayRange(monSat)).isEqualTo("Mon-Sat")
  }

  @Test
  fun `formatDayRange non-contiguous days listed individually`() {
    val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    assertThat(formatDayRange(days)).isEqualTo("Mon, Wed, Fri")
  }

  @Test
  fun `formatDayRange single day`() {
    assertThat(formatDayRange(setOf(DayOfWeek.TUESDAY))).isEqualTo("Tue")
  }

  @Test
  fun `formatDayRange two contiguous days listed individually`() {
    val days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
    assertThat(formatDayRange(days)).isEqualTo("Mon, Tue")
  }

  @Test
  fun `formatDayRange empty returns empty string`() {
    assertThat(formatDayRange(emptySet())).isEmpty()
  }

  @Test
  fun `formatLimit zero returns empty`() {
    assertThat(formatLimit(0)).isEmpty()
  }

  @Test
  fun `formatLimit 30 minutes`() {
    assertThat(formatLimit(30)).isEqualTo("30 min")
  }

  @Test
  fun `formatLimit 60 minutes returns 1 hr`() {
    assertThat(formatLimit(60)).isEqualTo("1 hr")
  }

  @Test
  fun `formatLimit 120 minutes returns 2 hrs`() {
    assertThat(formatLimit(120)).isEqualTo("2 hrs")
  }

  @Test
  fun `formatLimit 90 minutes returns fractional`() {
    assertThat(formatLimit(90)).isEqualTo("1.5 hrs")
  }

  @Test
  fun `formatRelativeTime negative returns now`() {
    assertThat(formatRelativeTime((-5).minutes)).isEqualTo("now")
  }

  @Test
  fun `formatRelativeTime 30 minutes`() {
    assertThat(formatRelativeTime(30.minutes)).isEqualTo("in 30 min")
  }

  @Test
  fun `formatRelativeTime 1 hour singular`() {
    assertThat(formatRelativeTime(1.hours)).isEqualTo("in 1 hr")
  }

  @Test
  fun `formatRelativeTime 5 hours`() {
    assertThat(formatRelativeTime(5.hours)).isEqualTo("in 5 hrs")
  }

  @Test
  fun `formatRelativeTime 1 day`() {
    assertThat(formatRelativeTime(1.days)).isEqualTo("in 1 day")
  }

  @Test
  fun `formatRelativeTime 3 days`() {
    assertThat(formatRelativeTime(3.days)).isEqualTo("in 3 days")
  }

  @Test
  fun `formatDurationCompact hours and minutes`() {
    assertThat(formatDurationCompact(1.hours + 30.minutes)).isEqualTo("1h 30m")
  }

  @Test
  fun `formatDurationCompact hours only`() {
    assertThat(formatDurationCompact(2.hours)).isEqualTo("2h")
  }

  @Test
  fun `formatDurationCompact minutes only`() {
    assertThat(formatDurationCompact(45.minutes)).isEqualTo("45m")
  }
}
