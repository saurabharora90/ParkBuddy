package dev.bongballe.parkbuddy.data.repository.utils

import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.model.IntervalSource
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.model.ProhibitionReason
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.DayOfWeek.THURSDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import kotlinx.datetime.LocalTime
import org.junit.Test

class TimelineResolverTest {

  private val weekdays = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)

  private fun limited(
    limitMinutes: Int,
    days: Set<DayOfWeek>,
    start: LocalTime,
    end: LocalTime,
    permitZones: List<String> = emptyList(),
  ) =
    ParkingInterval(
      type = IntervalType.Limited(limitMinutes),
      days = days,
      startTime = start,
      endTime = end,
      exemptPermitZones = permitZones,
      source = IntervalSource.REGULATION,
    )

  private fun metered(limitMinutes: Int, days: Set<DayOfWeek>, start: LocalTime, end: LocalTime) =
    ParkingInterval(
      type = IntervalType.Metered(limitMinutes),
      days = days,
      startTime = start,
      endTime = end,
      source = IntervalSource.METER,
    )

  private fun tow(days: Set<DayOfWeek>, start: LocalTime, end: LocalTime) =
    ParkingInterval(
      type = IntervalType.Forbidden(ProhibitionReason.TOW_AWAY),
      days = days,
      startTime = start,
      endTime = end,
      source = IntervalSource.TOW,
    )

  private fun forbidden(
    reason: ProhibitionReason,
    days: Set<DayOfWeek>,
    start: LocalTime,
    end: LocalTime,
  ) =
    ParkingInterval(
      type = IntervalType.Forbidden(reason),
      days = days,
      startTime = start,
      endTime = end,
      source = IntervalSource.REGULATION,
    )

  @Test
  fun `empty input produces empty timeline`() {
    assertThat(TimelineResolver.resolve(emptyList())).isEmpty()
  }

  @Test
  fun `single regulation produces single LIMITED interval`() {
    val input = listOf(limited(120, weekdays, LocalTime(8, 0), LocalTime(18, 0)))
    val result = TimelineResolver.resolve(input)

    assertThat(result).hasSize(1)
    val interval = result[0]
    assertThat(interval.type).isEqualTo(IntervalType.Limited(120))
    assertThat(interval.days).isEqualTo(weekdays)
    assertThat(interval.startTime).isEqualTo(LocalTime(8, 0))
    assertThat(interval.endTime).isEqualTo(LocalTime(18, 0))
  }

  @Test
  fun `single meter produces METERED interval`() {
    val input = listOf(metered(60, weekdays, LocalTime(9, 0), LocalTime(18, 0)))
    val result = TimelineResolver.resolve(input)

    assertThat(result).hasSize(1)
    assertThat(result[0].type).isEqualTo(IntervalType.Metered(60))
  }

  @Test
  fun `tow zone produces FORBIDDEN interval`() {
    val input = listOf(tow(weekdays, LocalTime(7, 0), LocalTime(9, 0)))
    val result = TimelineResolver.resolve(input)

    assertThat(result).hasSize(1)
    assertThat(result[0].type).isInstanceOf(IntervalType.Forbidden::class.java)
  }

  @Test
  fun `overlapping tow and operating schedule, tow wins during tow window`() {
    val input =
      listOf(
        tow(weekdays, LocalTime(7, 0), LocalTime(9, 0)),
        metered(120, weekdays, LocalTime(7, 0), LocalTime(18, 0)),
      )
    val result = TimelineResolver.resolve(input)

    // Should produce: FORBIDDEN 7-9, METERED 9-18
    val forbiddenIntervals = result.filter { it.type is IntervalType.Forbidden }
    val meteredIntervals = result.filter { it.type is IntervalType.Metered }

    assertThat(forbiddenIntervals).hasSize(1)
    assertThat(forbiddenIntervals[0].startTime).isEqualTo(LocalTime(7, 0))
    assertThat(forbiddenIntervals[0].endTime).isEqualTo(LocalTime(9, 0))

    assertThat(meteredIntervals).hasSize(1)
    assertThat(meteredIntervals[0].startTime).isEqualTo(LocalTime(9, 0))
    assertThat(meteredIntervals[0].endTime).isEqualTo(LocalTime(18, 0))
  }

  @Test
  fun `overlapping 30min meter and 2hr regulation, meter wins (higher priority)`() {
    val input =
      listOf(
        limited(120, weekdays, LocalTime(8, 0), LocalTime(18, 0)),
        metered(30, weekdays, LocalTime(8, 0), LocalTime(18, 0)),
      )
    val result = TimelineResolver.resolve(input)

    // METERED (priority 2) > LIMITED (priority 1)
    assertThat(result).hasSize(1)
    assertThat(result[0].type).isEqualTo(IntervalType.Metered(30))
  }

  @Test
  fun `multiple identical regulations dedup into one interval (California St scenario)`() {
    // California St has 12 regulation geometries matching the same CNN, all identical
    val dupes =
      (1..12).map {
        limited(
          120,
          setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY),
          LocalTime(8, 0),
          LocalTime(21, 0),
          permitZones = listOf("C"),
        )
      }
    val result = TimelineResolver.resolve(dupes)

    assertThat(result).hasSize(1)
    assertThat(result[0].type).isEqualTo(IntervalType.Limited(120))
    assertThat(result[0].exemptPermitZones).containsExactly("C")
  }

  @Test
  fun `different days with same window merge into single interval`() {
    val input =
      listOf(
        metered(
          720,
          setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY),
          LocalTime(7, 0),
          LocalTime(22, 0),
        ),
        metered(720, setOf(SUNDAY), LocalTime(12, 0), LocalTime(18, 0)),
      )
    val result = TimelineResolver.resolve(input)

    // Mon-Sat 7-22 is one interval, Sun 12-18 is another (different windows)
    assertThat(result).hasSize(2)
  }

  @Test
  fun `RPP areas carried through to interval`() {
    val input =
      listOf(
        limited(120, weekdays, LocalTime(8, 0), LocalTime(18, 0), permitZones = listOf("A", "G"))
      )
    val result = TimelineResolver.resolve(input)

    assertThat(result[0].exemptPermitZones).containsExactly("A", "G")
  }

  @Test
  fun `partial overlap, higher priority punches through`() {
    // Limited 8-18, Forbidden 12-14 (e.g. loading zone midday)
    val input =
      listOf(
        limited(120, setOf(MONDAY), LocalTime(8, 0), LocalTime(18, 0)),
        forbidden(ProhibitionReason.NO_PARKING, setOf(MONDAY), LocalTime(12, 0), LocalTime(14, 0)),
      )
    val result = TimelineResolver.resolve(input)

    // Should produce: LIMITED 8-12, FORBIDDEN 12-14, LIMITED 14-18
    val types = result.map { it.type }
    assertThat(types).hasSize(3)
    assertThat(types[0]).isEqualTo(IntervalType.Limited(120))
    assertThat(types[1]).isInstanceOf(IntervalType.Forbidden::class.java)
    assertThat(types[2]).isEqualTo(IntervalType.Limited(120))

    assertThat(result[0].endTime).isEqualTo(LocalTime(12, 0))
    assertThat(result[1].startTime).isEqualTo(LocalTime(12, 0))
    assertThat(result[1].endTime).isEqualTo(LocalTime(14, 0))
    assertThat(result[2].startTime).isEqualTo(LocalTime(14, 0))
    assertThat(result[2].endTime).isEqualTo(LocalTime(18, 0))
  }

  @Test
  fun `Embarcadero scenario, metered with tow and different Sunday window`() {
    // Real data: Mon-Sat 7AM-10PM (720min), Sun 12PM-6PM (720min)
    val monSat = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY)
    val input =
      listOf(
        metered(720, monSat, LocalTime(7, 0), LocalTime(22, 0)),
        metered(720, setOf(SUNDAY), LocalTime(12, 0), LocalTime(18, 0)),
      )
    val result = TimelineResolver.resolve(input)

    assertThat(result).hasSize(2)

    val monSatInterval = result.first { MONDAY in it.days }
    assertThat(monSatInterval.type).isEqualTo(IntervalType.Metered(720))
    assertThat(monSatInterval.startTime).isEqualTo(LocalTime(7, 0))
    assertThat(monSatInterval.endTime).isEqualTo(LocalTime(22, 0))
    assertThat(monSatInterval.days).isEqualTo(monSat)

    val sunInterval = result.first { SUNDAY in it.days }
    assertThat(sunInterval.type).isEqualTo(IntervalType.Metered(720))
    assertThat(sunInterval.startTime).isEqualTo(LocalTime(12, 0))
    assertThat(sunInterval.endTime).isEqualTo(LocalTime(18, 0))
  }

  @Test
  fun `within same priority tier shorter limit wins`() {
    // Two LIMITED overlapping: 30min and 120min
    val input =
      listOf(
        limited(120, setOf(MONDAY), LocalTime(8, 0), LocalTime(18, 0)),
        limited(30, setOf(MONDAY), LocalTime(8, 0), LocalTime(18, 0)),
      )
    val result = TimelineResolver.resolve(input)

    assertThat(result).hasSize(1)
    assertThat(result[0].type).isEqualTo(IntervalType.Limited(30))
  }
}
