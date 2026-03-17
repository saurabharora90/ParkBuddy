package dev.parkbuddy.feature.map

import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.IntervalSource
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ProhibitionReason
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.bongballe.parkbuddy.model.Weekday
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the [evaluate] pure function in SpotDetailViewModel.
 *
 * These are deterministic: fixed time, fixed timezone, no coroutines.
 */
@RunWith(RobolectricTestRunner::class)
class SpotDetailViewModelTest {

  private val zone = TimeZone.of("America/Los_Angeles")

  // Wednesday 2 PM
  private val wednesdayAfternoon =
    LocalDateTime(LocalDate(2026, 3, 18), LocalTime(14, 0)).toInstant(zone)

  // Wednesday 11 PM (after enforcement ends)
  private val wednesdayLateNight =
    LocalDateTime(LocalDate(2026, 3, 18), LocalTime(23, 0)).toInstant(zone)

  // Sunday 10 PM (no weekday enforcement)
  private val sundayNight = LocalDateTime(LocalDate(2026, 3, 15), LocalTime(22, 0)).toInstant(zone)

  // Monday 1 AM (cleaning starts at 12 AM, in 1 hour from Sunday 11 PM)
  private val mondayDuringCleaning =
    LocalDateTime(LocalDate(2026, 3, 16), LocalTime(1, 0)).toInstant(zone)

  private val geometry =
    Geometry(type = "Line", coordinates = listOf(listOf(-122.4, 37.8), listOf(-122.4, 37.81)))

  private fun makeSpot(
    timeline: List<ParkingInterval> = emptyList(),
    sweepingSchedules: List<SweepingSchedule> = emptyList(),
    rppAreas: List<String> = emptyList(),
  ) =
    ParkingSpot(
      objectId = "test",
      geometry = geometry,
      streetName = "Test St",
      blockLimits = "A - B",
      neighborhood = "Test",
      rppAreas = rppAreas,
      sweepingCnn = "1",
      sweepingSide = StreetSide.LEFT,
      sweepingSchedules = sweepingSchedules,
      timeline = timeline,
    )

  private val monFri =
    setOf(
      DayOfWeek.MONDAY,
      DayOfWeek.TUESDAY,
      DayOfWeek.WEDNESDAY,
      DayOfWeek.THURSDAY,
      DayOfWeek.FRIDAY,
    )

  // -- Free / Unrestricted --

  @Test
  fun `empty timeline returns Unrestricted`() {
    val spot = makeSpot()
    val state = evaluate(spot, null, wednesdayAfternoon, zone)

    assertThat(state.restrictionState)
      .isInstanceOf(ParkingRestrictionState.Unrestricted::class.java)
    assertThat(state.upcoming).isNull()
    assertThat(state.isImminent).isFalse()
    assertThat(state.timelineSegments).isEmpty()
  }

  @Test
  fun `outside enforcement hours returns PendingTimed when next interval exists`() {
    val spot =
      makeSpot(
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Limited(timeLimitMinutes = 120),
              days = monFri,
              startTime = LocalTime(8, 0),
              endTime = LocalTime(18, 0),
              source = IntervalSource.REGULATION,
            )
          )
      )

    // Wed 11 PM: limited rule ended, next is Thu 8 AM -> PendingTimed
    val state = evaluate(spot, null, wednesdayLateNight, zone)

    assertThat(state.restrictionState)
      .isInstanceOf(ParkingRestrictionState.PendingTimed::class.java)
  }

  @Test
  fun `weekend with weekday-only rules returns PendingTimed`() {
    val spot =
      makeSpot(
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Metered(timeLimitMinutes = 60),
              days = monFri,
              startTime = LocalTime(9, 0),
              endTime = LocalTime(18, 0),
              source = IntervalSource.METER,
            )
          )
      )

    // Sun 10 PM: no active rule, next is Mon 9 AM metered -> PendingTimed
    val state = evaluate(spot, null, sundayNight, zone)

    assertThat(state.restrictionState)
      .isInstanceOf(ParkingRestrictionState.PendingTimed::class.java)
  }

  // -- Imminent warning --

  @Test
  fun `imminent cleaning detected within 3 hours`() {
    // Sunday 11 PM, cleaning at Monday 12 AM (1 hour away)
    val sundayLateNight = LocalDateTime(LocalDate(2026, 3, 15), LocalTime(23, 0)).toInstant(zone)

    val spot =
      makeSpot(
        sweepingSchedules =
          listOf(
            SweepingSchedule(
              weekday = Weekday.Mon,
              fromHour = 0,
              toHour = 2,
              week1 = true,
              week2 = true,
              week3 = true,
              week4 = true,
              week5 = true,
              holidays = false,
            )
          )
      )

    val state = evaluate(spot, null, sundayLateNight, zone)

    assertThat(state.isImminent).isTrue()
    assertThat(state.upcoming).isNotNull()
    assertThat(requireNotNull(state.upcoming).reason).isEqualTo("Street cleaning")
  }

  @Test
  fun `non-imminent cleaning more than 3 hours away`() {
    // Wednesday 2 PM, cleaning Thursday 12 AM
    val spot =
      makeSpot(
        sweepingSchedules =
          listOf(
            SweepingSchedule(
              weekday = Weekday.Thu,
              fromHour = 0,
              toHour = 2,
              week1 = true,
              week2 = true,
              week3 = true,
              week4 = true,
              week5 = true,
              holidays = false,
            )
          )
      )

    val state = evaluate(spot, null, wednesdayAfternoon, zone)

    assertThat(state.isImminent).isFalse()
  }

  // -- Active sweeping --

  @Test
  fun `active sweeping returns CleaningActive`() {
    val spot =
      makeSpot(
        sweepingSchedules =
          listOf(
            SweepingSchedule(
              weekday = Weekday.Mon,
              fromHour = 0,
              toHour = 2,
              week1 = true,
              week2 = true,
              week3 = true,
              week4 = true,
              week5 = true,
              holidays = false,
            )
          )
      )

    val state = evaluate(spot, null, mondayDuringCleaning, zone)

    assertThat(state.restrictionState)
      .isInstanceOf(ParkingRestrictionState.CleaningActive::class.java)
  }

  // -- Active timed (limited) --

  @Test
  fun `active limited interval returns ActiveTimed`() {
    val spot =
      makeSpot(
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Limited(timeLimitMinutes = 120),
              days = monFri,
              startTime = LocalTime(8, 0),
              endTime = LocalTime(18, 0),
              source = IntervalSource.REGULATION,
            )
          )
      )

    val state = evaluate(spot, null, wednesdayAfternoon, zone)

    assertThat(state.restrictionState).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val timed = state.restrictionState as ParkingRestrictionState.ActiveTimed
    assertThat(timed.paymentRequired).isFalse()
  }

  // -- Active metered --

  @Test
  fun `active metered interval returns ActiveTimed with payment required`() {
    val spot =
      makeSpot(
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Metered(timeLimitMinutes = 60),
              days = monFri,
              startTime = LocalTime(9, 0),
              endTime = LocalTime(18, 0),
              source = IntervalSource.METER,
            )
          )
      )

    val state = evaluate(spot, null, wednesdayAfternoon, zone)

    assertThat(state.restrictionState).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val timed = state.restrictionState as ParkingRestrictionState.ActiveTimed
    assertThat(timed.paymentRequired).isTrue()
  }

  // -- Forbidden --

  @Test
  fun `active forbidden interval returns Forbidden`() {
    val spot =
      makeSpot(
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Forbidden(reason = ProhibitionReason.TOW_AWAY),
              days = monFri,
              startTime = LocalTime(7, 0),
              endTime = LocalTime(19, 0),
              source = IntervalSource.TOW,
            )
          )
      )

    val state = evaluate(spot, null, wednesdayAfternoon, zone)

    assertThat(state.restrictionState).isInstanceOf(ParkingRestrictionState.Forbidden::class.java)
  }

  // -- Permit safe --

  @Test
  fun `permit zone exempt returns PermitSafe`() {
    val spot =
      makeSpot(
        rppAreas = listOf("A"),
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Limited(timeLimitMinutes = 120),
              days = monFri,
              startTime = LocalTime(8, 0),
              endTime = LocalTime(18, 0),
              exemptPermitZones = listOf("A"),
              source = IntervalSource.REGULATION,
            )
          ),
      )

    val state = evaluate(spot, "A", wednesdayAfternoon, zone)

    assertThat(state.restrictionState).isInstanceOf(ParkingRestrictionState.PermitSafe::class.java)
    assertThat(state.isPermitExempt).isTrue()
    assertThat(state.permitZone).isEqualTo("A")
  }

  @Test
  fun `permit safe upcoming skips metered and limited intervals`() {
    val spot =
      makeSpot(
        rppAreas = listOf("A"),
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Metered(timeLimitMinutes = 60),
              days = monFri,
              startTime = LocalTime(9, 0),
              endTime = LocalTime(18, 0),
              exemptPermitZones = listOf("A"),
              source = IntervalSource.METER,
            ),
            ParkingInterval(
              type = IntervalType.Forbidden(reason = ProhibitionReason.TOW_AWAY),
              days = monFri,
              startTime = LocalTime(7, 0),
              endTime = LocalTime(9, 0),
              exemptPermitZones = emptyList(),
              source = IntervalSource.TOW,
            ),
          ),
      )

    // Wednesday 6 AM: metered starts at 9 AM (3 hrs), tow at 7 AM (1 hr)
    // Permit holder should see tow (not metered) as next enforcement
    val wednesday6am = LocalDateTime(LocalDate(2026, 3, 18), LocalTime(6, 0)).toInstant(zone)
    val state = evaluate(spot, "A", wednesday6am, zone)

    assertThat(state.upcoming).isNotNull()
    assertThat(requireNotNull(state.upcoming).reason).isEqualTo("Tow Away Zone")
  }

  @Test
  fun `permit safe intervals not marked active`() {
    val spot =
      makeSpot(
        rppAreas = listOf("A"),
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Metered(timeLimitMinutes = 60),
              days = monFri,
              startTime = LocalTime(9, 0),
              endTime = LocalTime(18, 0),
              exemptPermitZones = listOf("A"),
              source = IntervalSource.METER,
            )
          ),
      )

    val state = evaluate(spot, "A", wednesdayAfternoon, zone)

    // Metered interval is active at 2 PM, but permit holder is exempt
    val meteredDisplay = state.sortedIntervals.first()
    assertThat(meteredDisplay.isActive).isFalse()
  }

  @Test
  fun `commercial zone with permit still returns Forbidden`() {
    val spot =
      makeSpot(
        rppAreas = listOf("Y"),
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Restricted(reason = ProhibitionReason.COMMERCIAL),
              days = monFri,
              startTime = LocalTime(7, 0),
              endTime = LocalTime(22, 0),
              exemptPermitZones = listOf("Y"),
              source = IntervalSource.REGULATION,
            )
          ),
      )

    val state = evaluate(spot, "Y", wednesdayAfternoon, zone)

    // Commercial zone should NOT be PermitSafe, even with matching permit
    assertThat(state.restrictionState).isInstanceOf(ParkingRestrictionState.Forbidden::class.java)
    assertThat(state.isPermitExempt).isFalse()
  }

  @Test
  fun `permit safe timeline segments exclude metered and limited`() {
    val spot =
      makeSpot(
        rppAreas = listOf("A"),
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Limited(timeLimitMinutes = 120),
              days = monFri,
              startTime = LocalTime(8, 0),
              endTime = LocalTime(18, 0),
              exemptPermitZones = listOf("A"),
              source = IntervalSource.REGULATION,
            ),
            ParkingInterval(
              type = IntervalType.Forbidden(reason = ProhibitionReason.TOW_AWAY),
              days = monFri,
              startTime = LocalTime(7, 0),
              endTime = LocalTime(8, 0),
              source = IntervalSource.TOW,
            ),
          ),
        sweepingSchedules =
          listOf(
            SweepingSchedule(
              weekday = Weekday.Wed,
              fromHour = 6,
              toHour = 7,
              week1 = true,
              week2 = true,
              week3 = true,
              week4 = true,
              week5 = true,
              holidays = false,
            )
          ),
      )

    val state = evaluate(spot, "A", wednesdayAfternoon, zone)

    assertThat(state.restrictionState).isInstanceOf(ParkingRestrictionState.PermitSafe::class.java)
    // Only forbidden (tow-away) + sweeping segments, limited is filtered out
    assertThat(state.timelineSegments).hasSize(2)
    assertThat(
        state.timelineSegments.all {
          it.intervalType == null || it.intervalType?.isProhibited == true
        }
      )
      .isTrue()
  }

  @Test
  fun `non-permit timeline segments include all interval types`() {
    val spot =
      makeSpot(
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Limited(timeLimitMinutes = 120),
              days = monFri,
              startTime = LocalTime(8, 0),
              endTime = LocalTime(18, 0),
              source = IntervalSource.REGULATION,
            ),
            ParkingInterval(
              type = IntervalType.Forbidden(reason = ProhibitionReason.TOW_AWAY),
              days = monFri,
              startTime = LocalTime(7, 0),
              endTime = LocalTime(8, 0),
              source = IntervalSource.TOW,
            ),
          ),
        sweepingSchedules =
          listOf(
            SweepingSchedule(
              weekday = Weekday.Wed,
              fromHour = 6,
              toHour = 7,
              week1 = true,
              week2 = true,
              week3 = true,
              week4 = true,
              week5 = true,
              holidays = false,
            )
          ),
      )

    // No permit zone: all 3 segments (limited + forbidden + sweeping)
    val state = evaluate(spot, null, wednesdayAfternoon, zone)

    assertThat(state.timelineSegments).hasSize(3)
  }

  // -- Timeline segments --

  @Test
  fun `timeline segments built for current day`() {
    val spot =
      makeSpot(
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Metered(timeLimitMinutes = 60),
              days = monFri,
              startTime = LocalTime(9, 0),
              endTime = LocalTime(18, 0),
              source = IntervalSource.METER,
            ),
            ParkingInterval(
              type = IntervalType.Forbidden(reason = ProhibitionReason.TOW_AWAY),
              days = monFri,
              startTime = LocalTime(7, 0),
              endTime = LocalTime(9, 0),
              source = IntervalSource.TOW,
            ),
          )
      )

    val state = evaluate(spot, null, wednesdayAfternoon, zone)

    assertThat(state.timelineSegments).hasSize(2)
    assertThat(state.timelineSegments[0].startMinute).isEqualTo(7 * 60)
    assertThat(state.timelineSegments[0].endMinute).isEqualTo(9 * 60)
    assertThat(state.timelineSegments[1].startMinute).isEqualTo(9 * 60)
    assertThat(state.timelineSegments[1].endMinute).isEqualTo(18 * 60)
  }

  @Test
  fun `no timeline segments on day without rules`() {
    val spot =
      makeSpot(
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Limited(timeLimitMinutes = 120),
              days = monFri,
              startTime = LocalTime(8, 0),
              endTime = LocalTime(18, 0),
              source = IntervalSource.REGULATION,
            )
          )
      )

    val state = evaluate(spot, null, sundayNight, zone)

    assertThat(state.timelineSegments).isEmpty()
  }

  // -- Sweeping display --

  @Test
  fun `sweeping display marks active schedule`() {
    val spot =
      makeSpot(
        sweepingSchedules =
          listOf(
            SweepingSchedule(
              weekday = Weekday.Mon,
              fromHour = 0,
              toHour = 2,
              week1 = true,
              week2 = true,
              week3 = true,
              week4 = true,
              week5 = true,
              holidays = false,
            )
          )
      )

    val state = evaluate(spot, null, mondayDuringCleaning, zone)

    assertThat(state.sweepingDisplay).hasSize(1)
    assertThat(state.sweepingDisplay[0].isActive).isTrue()
    assertThat(state.sweepingDisplay[0].relativeTimeText).isNull()
  }

  @Test
  fun `sweeping display shows relative time for inactive schedule`() {
    val spot =
      makeSpot(
        sweepingSchedules =
          listOf(
            SweepingSchedule(
              weekday = Weekday.Thu,
              fromHour = 8,
              toHour = 10,
              week1 = true,
              week2 = true,
              week3 = true,
              week4 = true,
              week5 = true,
              holidays = false,
            )
          )
      )

    // Wednesday 2 PM, Thursday 8 AM is ~18 hours away
    val state = evaluate(spot, null, wednesdayAfternoon, zone)

    assertThat(state.sweepingDisplay).hasSize(1)
    assertThat(state.sweepingDisplay[0].isActive).isFalse()
    assertThat(state.sweepingDisplay[0].relativeTimeText).isNotNull()
    assertThat(state.sweepingDisplay[0].relativeTimeText).startsWith("in")
  }

  // -- Sorted intervals --

  @Test
  fun `sorted intervals marked active or inactive`() {
    val spot =
      makeSpot(
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Metered(timeLimitMinutes = 60),
              days = monFri,
              startTime = LocalTime(9, 0),
              endTime = LocalTime(18, 0),
              source = IntervalSource.METER,
            ),
            ParkingInterval(
              type = IntervalType.Forbidden(reason = ProhibitionReason.TOW_AWAY),
              days = monFri,
              startTime = LocalTime(7, 0),
              endTime = LocalTime(9, 0),
              source = IntervalSource.TOW,
            ),
          )
      )

    // Wednesday 2 PM: metered is active (9-18), tow is not (7-9)
    val state = evaluate(spot, null, wednesdayAfternoon, zone)

    assertThat(state.sortedIntervals).hasSize(2)
    val towInterval = state.sortedIntervals.first { it.interval.type is IntervalType.Forbidden }
    val meteredInterval = state.sortedIntervals.first { it.interval.type is IntervalType.Metered }
    assertThat(towInterval.isActive).isFalse()
    assertThat(meteredInterval.isActive).isTrue()
  }

  // -- Current minute --

  @Test
  fun `current minute computed correctly`() {
    val state = evaluate(makeSpot(), null, wednesdayAfternoon, zone)
    // 2 PM = 14 * 60 = 840
    assertThat(state.currentMinute).isEqualTo(14 * 60)
  }

  // -- Sweeping priority --

  @Test
  fun `sweeping during forbidden returns CleaningActive not Forbidden`() {
    val spot =
      makeSpot(
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Forbidden(reason = ProhibitionReason.TOW_AWAY),
              days = setOf(DayOfWeek.MONDAY),
              startTime = LocalTime(0, 0),
              endTime = LocalTime(6, 0),
              source = IntervalSource.TOW,
            )
          ),
        sweepingSchedules =
          listOf(
            SweepingSchedule(
              weekday = Weekday.Mon,
              fromHour = 0,
              toHour = 2,
              week1 = true,
              week2 = true,
              week3 = true,
              week4 = true,
              week5 = true,
              holidays = false,
            )
          ),
      )

    val state = evaluate(spot, null, mondayDuringCleaning, zone)

    // CleaningActive takes priority, UI gets a single state to render
    assertThat(state.restrictionState)
      .isInstanceOf(ParkingRestrictionState.CleaningActive::class.java)
  }

  // -- Browse mode expiry --

  @Test
  fun `browse mode ActiveTimed expiry is based on now, not window end`() {
    val spot =
      makeSpot(
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Limited(timeLimitMinutes = 120),
              days = monFri,
              startTime = LocalTime(8, 0),
              endTime = LocalTime(18, 0),
              source = IntervalSource.REGULATION,
            )
          )
      )

    // Browse mode: evaluate passes now as parkedAt
    val state = evaluate(spot, null, wednesdayAfternoon, zone)

    assertThat(state.restrictionState).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val timed = state.restrictionState as ParkingRestrictionState.ActiveTimed
    // 2 PM + 120 min = 4 PM, not 6 PM (window end)
    val expected = LocalDateTime(LocalDate(2026, 3, 18), LocalTime(16, 0)).toInstant(zone)
    assertThat(timed.expiry).isEqualTo(expected)
  }

  // -- isImminent for ActiveTimed --

  @Test
  fun `isImminent is true when ActiveTimed expiry is less than 3 hours away`() {
    // Wed 4:30 PM, 2hr limit started at 2 PM. Expiry = 4 PM (30 min ago, but evaluate
    // passes now as parkedAt so expiry = 4:30 PM + 120 min = 6:30 PM).
    // Actually, at 4:30 PM with 120 min limit, expiry = 6:30 PM which is 2 hours away.
    val wednesday430 = LocalDateTime(LocalDate(2026, 3, 18), LocalTime(16, 30)).toInstant(zone)
    val spot =
      makeSpot(
        timeline =
          listOf(
            ParkingInterval(
              type = IntervalType.Limited(timeLimitMinutes = 120),
              days = monFri,
              startTime = LocalTime(8, 0),
              endTime = LocalTime(18, 0),
              source = IntervalSource.REGULATION,
            )
          )
      )

    val state = evaluate(spot, null, wednesday430, zone)

    assertThat(state.restrictionState).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    // Expiry is 2 hours away, which is < 3 hour threshold
    assertThat(state.isImminent).isTrue()
    assertThat(state.upcoming).isNotNull()
    assertThat(requireNotNull(state.upcoming).reason).contains("expires")
  }
}
