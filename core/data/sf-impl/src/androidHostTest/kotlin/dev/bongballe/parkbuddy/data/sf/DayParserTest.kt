package dev.bongballe.parkbuddy.data.sf

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.DayOfWeek.THURSDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import org.junit.Test

class DayParserTest {

  private val weekdays = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)
  private val monThroughSat = weekdays + SATURDAY
  private val allDays = DayOfWeek.entries.toSet()

  // ===== Regulation day parsing =====

  @Test
  fun `parseRegulationDays with null returns all 7 days`() {
    assertThat(DayParser.parseRegulationDays(null)).isEqualTo(allDays)
  }

  @Test
  fun `parseRegulationDays with blank returns all 7 days`() {
    assertThat(DayParser.parseRegulationDays("")).isEqualTo(allDays)
    assertThat(DayParser.parseRegulationDays("  ")).isEqualTo(allDays)
  }

  @Test
  fun `parseRegulationDays with M-F returns Mon through Fri`() {
    assertThat(DayParser.parseRegulationDays("M-F")).isEqualTo(weekdays)
  }

  @Test
  fun `parseRegulationDays with lowercase m-f returns Mon through Fri`() {
    assertThat(DayParser.parseRegulationDays("m-f")).isEqualTo(weekdays)
  }

  @Test
  fun `parseRegulationDays with M-Sa returns Mon through Sat`() {
    assertThat(DayParser.parseRegulationDays("M-Sa")).isEqualTo(monThroughSat)
  }

  @Test
  fun `parseRegulationDays with M-Su returns all 7 days`() {
    assertThat(DayParser.parseRegulationDays("M-Su")).isEqualTo(allDays)
  }

  @Test
  fun `parseRegulationDays with M-S returns Mon through Sat`() {
    // "M-S" means Mon-Sat in SF data (11 records). "S" maps to SATURDAY via SHORT_TO_DAY.
    assertThat(DayParser.parseRegulationDays("M-S")).isEqualTo(monThroughSat)
  }

  @Test
  fun `parseRegulationDays with M-SU returns all 7 days`() {
    assertThat(DayParser.parseRegulationDays("M-SU")).isEqualTo(allDays)
  }

  @Test
  fun `parseRegulationDays with M comma TH returns Mon and Thu`() {
    assertThat(DayParser.parseRegulationDays("M, TH")).isEqualTo(setOf(MONDAY, THURSDAY))
  }

  @Test
  fun `parseRegulationDays with Sa returns Saturday only`() {
    assertThat(DayParser.parseRegulationDays("Sa")).isEqualTo(setOf(SATURDAY))
  }

  // ===== Meter day parsing =====

  @Test
  fun `parseMeterDays with null returns all 7 days`() {
    assertThat(DayParser.parseMeterDays(null)).isEqualTo(allDays)
  }

  @Test
  fun `parseMeterDays with standard comma-separated days`() {
    assertThat(DayParser.parseMeterDays("Mo,Tu,We,Th,Fr")).isEqualTo(weekdays)
  }

  @Test
  fun `parseMeterDays with Saturday only`() {
    assertThat(DayParser.parseMeterDays("Sa")).isEqualTo(setOf(SATURDAY))
  }

  @Test
  fun `parseMeterDays with Sunday only`() {
    assertThat(DayParser.parseMeterDays("Su")).isEqualTo(setOf(SUNDAY))
  }

  @Test
  fun `parseMeterDays with all days comma-separated`() {
    assertThat(DayParser.parseMeterDays("Mo,Tu,We,Th,Fr,Sa,Su")).isEqualTo(allDays)
  }

  @Test
  fun `parseMeterDays with School Days returns Mon through Fri`() {
    assertThat(DayParser.parseMeterDays("School Days")).isEqualTo(weekdays)
  }

  @Test
  fun `parseMeterDays with Business Hours returns Mon through Fri`() {
    assertThat(DayParser.parseMeterDays("Business Hours")).isEqualTo(weekdays)
  }

  @Test
  fun `parseMeterDays with Giants Day returns null (unevaluable)`() {
    assertThat(DayParser.parseMeterDays("Giants Day")).isNull()
  }

  @Test
  fun `parseMeterDays with Giants Night returns null (unevaluable)`() {
    assertThat(DayParser.parseMeterDays("Giants Night")).isNull()
  }

  @Test
  fun `parseMeterDays with Performance returns null (unevaluable)`() {
    assertThat(DayParser.parseMeterDays("Performance")).isNull()
  }

  @Test
  fun `parseMeterDays with Posted Events returns null (unevaluable)`() {
    assertThat(DayParser.parseMeterDays("Posted Events")).isNull()
  }

  @Test
  fun `parseMeterDays with Posted Services returns null (unevaluable)`() {
    assertThat(DayParser.parseMeterDays("Posted Services")).isNull()
  }

  @Test
  fun `parseMeterDays with weekday plus Saturday`() {
    assertThat(DayParser.parseMeterDays("Mo,Tu,We,Th,Fr,Sa")).isEqualTo(monThroughSat)
  }
}
