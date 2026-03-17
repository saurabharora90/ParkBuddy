package dev.bongballe.parkbuddy.data.repository.utils

import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.fixtures.createSpot
import dev.bongballe.parkbuddy.fixtures.createSweepingSchedule
import dev.bongballe.parkbuddy.model.IntervalSource
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ProhibitionReason
import dev.bongballe.parkbuddy.model.Weekday
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test

class ParkingRestrictionEvaluatorTest {

  private val zone = TimeZone.of("America/Los_Angeles")
  private val weekdays =
    setOf(
      DayOfWeek.MONDAY,
      DayOfWeek.TUESDAY,
      DayOfWeek.WEDNESDAY,
      DayOfWeek.THURSDAY,
      DayOfWeek.FRIDAY,
    )

  /** M-F 8am-6pm, 2hr limit, no payment. */
  private val weekdayLimited =
    ParkingInterval(
      type = IntervalType.Limited(timeLimitMinutes = 120),
      days = weekdays,
      startTime = LocalTime(8, 0),
      endTime = LocalTime(18, 0),
      exemptPermitZones = listOf("A"),
      source = IntervalSource.REGULATION,
    )

  /** M-F 9am-6pm, metered, 60 min limit. */
  private val weekdayMetered =
    ParkingInterval(
      type = IntervalType.Metered(timeLimitMinutes = 60),
      days = weekdays,
      startTime = LocalTime(9, 0),
      endTime = LocalTime(18, 0),
      source = IntervalSource.METER,
    )

  /** M-Tu 7am-9am, Tow Away Zone. */
  private val towAway =
    ParkingInterval(
      type = IntervalType.Forbidden(ProhibitionReason.TOW_AWAY),
      days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
      startTime = LocalTime(7, 0),
      endTime = LocalTime(9, 0),
      source = IntervalSource.TOW,
    )

  private fun dateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
    LocalDateTime(year, month, day, hour, minute).toInstant(zone)

  // ---------------------------------------------------------------------------
  // Sweeping (stays separate from timeline)
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate returns CleaningActive when street cleaning is in progress`() {
    val now = dateTime(2024, 1, 1, 9, 0) // Monday 9 AM
    val sweeping = createSweepingSchedule(Weekday.Mon, fromHour = 8, toHour = 10)
    val spot = createSpot(id = "1", sweepingSchedules = listOf(sweeping))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.CleaningActive::class.java)
    val cleaning = state as ParkingRestrictionState.CleaningActive
    assertThat(cleaning.cleaningEnd).isEqualTo(dateTime(2024, 1, 1, 10, 0))
  }

  // ---------------------------------------------------------------------------
  // Unrestricted (no active interval)
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate returns Unrestricted when timeline is empty`() {
    val now = dateTime(2024, 1, 1, 12, 0)
    val spot = createSpot(id = "1", limitMinutes = null, timeline = emptyList())

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.Unrestricted::class.java)
  }

  @Test
  fun `evaluate returns PendingTimed when current time is after enforcement hours`() {
    val now = dateTime(2024, 1, 1, 20, 0) // Monday 8 PM, outside 8am-6pm window
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(weekdayLimited))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    // Next enforcement starts Tuesday 8 AM, so the evaluator returns PendingTimed
    assertThat(state).isInstanceOf(ParkingRestrictionState.PendingTimed::class.java)
  }

  @Test
  fun `evaluate returns PendingTimed when no interval matches the day`() {
    val now = dateTime(2024, 1, 6, 12, 0) // Saturday noon, interval is M-F
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(weekdayLimited))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    // Next enforcement starts Monday 8 AM, so the evaluator returns PendingTimed
    assertThat(state).isInstanceOf(ParkingRestrictionState.PendingTimed::class.java)
  }

  // ---------------------------------------------------------------------------
  // Open interval
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate returns Unrestricted for an explicitly Open interval`() {
    val now = dateTime(2024, 1, 1, 12, 0)
    val openInterval =
      ParkingInterval(
        type = IntervalType.Open,
        days = weekdays,
        startTime = LocalTime(8, 0),
        endTime = LocalTime(18, 0),
        source = IntervalSource.REGULATION,
      )
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(openInterval))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.Unrestricted::class.java)
  }

  // ---------------------------------------------------------------------------
  // Permit exemption
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate returns PermitSafe when user permit matches exempt zone on active interval`() {
    val now = dateTime(2024, 1, 1, 12, 0) // Monday noon, inside weekdayLimited (exempt: "A")
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(weekdayLimited))

    val state = ParkingRestrictionEvaluator.evaluate(spot, "A", now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.PermitSafe::class.java)
  }

  @Test
  fun `evaluate does not return PermitSafe when user permit does not match`() {
    val now = dateTime(2024, 1, 1, 12, 0)
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(weekdayLimited))

    val state = ParkingRestrictionEvaluator.evaluate(spot, "Z", now, now, zone)

    assertThat(state).isNotInstanceOf(ParkingRestrictionState.PermitSafe::class.java)
  }

  @Test
  fun `evaluate returns PermitSafe when interval has multiple exempt zones and user has one`() {
    val now = dateTime(2024, 1, 1, 12, 0)
    val interval = weekdayLimited.copy(exemptPermitZones = listOf("A", "B", "C"))
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(interval))

    val stateB = ParkingRestrictionEvaluator.evaluate(spot, "B", now, now, zone)
    assertThat(stateB).isInstanceOf(ParkingRestrictionState.PermitSafe::class.java)

    val stateD = ParkingRestrictionEvaluator.evaluate(spot, "D", now, now, zone)
    assertThat(stateD).isNotInstanceOf(ParkingRestrictionState.PermitSafe::class.java)
  }

  // ---------------------------------------------------------------------------
  // Forbidden
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate returns Forbidden during a tow-away interval`() {
    val now = dateTime(2024, 1, 1, 8, 0) // Monday 8 AM, inside tow-away 7-9am
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(towAway))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.Forbidden::class.java)
    assertThat((state as ParkingRestrictionState.Forbidden).reason)
      .isEqualTo(ProhibitionReason.TOW_AWAY)
  }

  @Test
  fun `evaluate returns Forbidden for a Restricted interval`() {
    val now = dateTime(2024, 1, 1, 12, 0)
    val restricted =
      ParkingInterval(
        type = IntervalType.Restricted(ProhibitionReason.COMMERCIAL),
        days = weekdays,
        startTime = LocalTime(8, 0),
        endTime = LocalTime(18, 0),
        source = IntervalSource.REGULATION,
      )
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(restricted))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.Forbidden::class.java)
    assertThat((state as ParkingRestrictionState.Forbidden).reason)
      .isEqualTo(ProhibitionReason.COMMERCIAL)
  }

  // ---------------------------------------------------------------------------
  // ActiveTimed (limited)
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate returns ActiveTimed when parked during a limited interval`() {
    val now = dateTime(2024, 1, 1, 14, 0) // Monday 2 PM
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(weekdayLimited))

    val state = ParkingRestrictionEvaluator.evaluate(spot, "Z", now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 16, 0)) // 2 PM + 120 min
    assertThat(active.paymentRequired).isFalse()
  }

  @Test
  fun `evaluate returns ActiveTimed with correct expiry when parked mid-window`() {
    val parkedAt = dateTime(2024, 1, 1, 10, 0) // Parked at 10 AM
    val now = dateTime(2024, 1, 1, 11, 0) // Now 11 AM
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(weekdayLimited))

    val state = ParkingRestrictionEvaluator.evaluate(spot, "Z", parkedAt, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    // Parked at 10 AM + 120 min limit = noon
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 12, 0))
  }

  @Test
  fun `evaluate uses window start when parked before window opens`() {
    val parkedAt = dateTime(2024, 1, 1, 6, 0) // Parked at 6 AM, before 8 AM window
    val now = dateTime(2024, 1, 1, 9, 0) // Now 9 AM, inside window
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(weekdayLimited))

    val state = ParkingRestrictionEvaluator.evaluate(spot, "Z", parkedAt, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    // Window started at 8 AM + 120 min = 10 AM
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 10, 0))
  }

  // ---------------------------------------------------------------------------
  // ActiveTimed (metered)
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate returns ActiveTimed with paymentRequired for metered interval`() {
    val now = dateTime(2024, 1, 1, 10, 0) // Monday 10 AM
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(weekdayMetered))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 11, 0)) // 10 AM + 60 min
    assertThat(active.paymentRequired).isTrue()
  }

  // ---------------------------------------------------------------------------
  // True limit: clamped by next prohibited interval
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate clamps expiry to next forbidden interval start`() {
    // Scenario: parked at 6:30 AM on Monday. Limited interval is 6am-9am (3hr limit).
    // But a Tow Away Zone starts at 7am. True deadline is 7 AM, not 9:30 AM.
    val now = dateTime(2024, 1, 1, 6, 30)
    val limited =
      ParkingInterval(
        type = IntervalType.Limited(timeLimitMinutes = 180),
        days = setOf(DayOfWeek.MONDAY),
        startTime = LocalTime(6, 0),
        endTime = LocalTime(9, 0),
        source = IntervalSource.REGULATION,
      )
    val forbidden =
      ParkingInterval(
        type = IntervalType.Forbidden(ProhibitionReason.TOW_AWAY),
        days = setOf(DayOfWeek.MONDAY),
        startTime = LocalTime(7, 0),
        endTime = LocalTime(9, 0),
        source = IntervalSource.TOW,
      )
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(limited, forbidden))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 7, 0))
  }

  @Test
  fun `evaluate does not clamp when forbidden interval is after the limit expiry`() {
    // Parked at 10 AM. 60-min limit. Forbidden at 6 PM. Expiry should be 11 AM, not clamped.
    val now = dateTime(2024, 1, 1, 10, 0)
    val limited =
      ParkingInterval(
        type = IntervalType.Limited(timeLimitMinutes = 60),
        days = setOf(DayOfWeek.MONDAY),
        startTime = LocalTime(8, 0),
        endTime = LocalTime(18, 0),
        source = IntervalSource.REGULATION,
      )
    val forbidden =
      ParkingInterval(
        type = IntervalType.Forbidden(ProhibitionReason.TOW_AWAY),
        days = setOf(DayOfWeek.MONDAY),
        startTime = LocalTime(18, 0),
        endTime = LocalTime(20, 0),
        source = IntervalSource.TOW,
      )
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(limited, forbidden))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 11, 0))
  }

  // ---------------------------------------------------------------------------
  // nextCleaning passthrough
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate returns null nextCleaning when no sweeping schedules`() {
    val now = dateTime(2024, 1, 1, 12, 0)
    val spot = createSpot(id = "1", limitMinutes = null, timeline = emptyList())

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state.nextCleaning).isNull()
  }

  @Test
  fun `evaluate populates nextCleaning when sweeping schedule exists`() {
    val now = dateTime(2024, 1, 1, 12, 0) // Monday noon
    val sweeping = createSweepingSchedule(Weekday.Tues, fromHour = 8, toHour = 10)
    val spot =
      createSpot(
        id = "1",
        limitMinutes = null,
        timeline = emptyList(),
        sweepingSchedules = listOf(sweeping),
      )

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.Unrestricted::class.java)
    assertThat(state.nextCleaning).isEqualTo(dateTime(2024, 1, 2, 8, 0))
  }

  @Test
  fun `evaluate populates nextCleaning on ActiveTimed state`() {
    val now = dateTime(2024, 1, 1, 14, 0) // Monday 2 PM
    val sweeping = createSweepingSchedule(Weekday.Tues, fromHour = 8, toHour = 10)
    val spot =
      createSpot(
        id = "1",
        limitMinutes = null,
        timeline = listOf(weekdayLimited),
        sweepingSchedules = listOf(sweeping),
      )

    val state = ParkingRestrictionEvaluator.evaluate(spot, "Z", now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    assertThat(state.nextCleaning).isEqualTo(dateTime(2024, 1, 2, 8, 0))
  }

  // ---------------------------------------------------------------------------
  // nextCleaning clamps ActiveTimed expiry
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate clamps ActiveTimed expiry to nextCleaning when cleaning is sooner`() {
    // Monday 2 PM, 2hr limit -> raw expiry 4 PM. Cleaning starts at 3 PM.
    // True expiry should be 3 PM (cleaning deadline).
    val now = dateTime(2024, 1, 1, 14, 0) // Monday 2 PM
    val sweeping = createSweepingSchedule(Weekday.Mon, fromHour = 15, toHour = 17)
    val spot =
      createSpot(
        id = "1",
        limitMinutes = null,
        timeline = listOf(weekdayLimited),
        sweepingSchedules = listOf(sweeping),
      )

    val state = ParkingRestrictionEvaluator.evaluate(spot, "Z", now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 15, 0))
  }

  // ---------------------------------------------------------------------------
  // ForbiddenUpcoming
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate returns ForbiddenUpcoming for upcoming tow-away zone`() {
    // Monday 6 AM. Tow-away starts at 7 AM. Should be ForbiddenUpcoming.
    val now = dateTime(2024, 1, 1, 6, 0) // Monday 6 AM
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(towAway))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ForbiddenUpcoming::class.java)
    val upcoming = state as ParkingRestrictionState.ForbiddenUpcoming
    assertThat(upcoming.reason).isEqualTo(ProhibitionReason.TOW_AWAY)
    assertThat(upcoming.startsAt).isEqualTo(dateTime(2024, 1, 1, 7, 0))
  }

  @Test
  fun `evaluate returns ForbiddenUpcoming for upcoming restricted interval on different day`() {
    // Saturday noon. Commercial M-F 7-10 PM. Next commercial starts Monday 7 AM.
    val now = dateTime(2024, 1, 6, 12, 0) // Saturday noon
    val commercial =
      ParkingInterval(
        type = IntervalType.Restricted(ProhibitionReason.COMMERCIAL),
        days = weekdays,
        startTime = LocalTime(7, 0),
        endTime = LocalTime(22, 0),
        source = IntervalSource.REGULATION,
      )
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(commercial))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ForbiddenUpcoming::class.java)
    val upcoming = state as ParkingRestrictionState.ForbiddenUpcoming
    assertThat(upcoming.reason).isEqualTo(ProhibitionReason.COMMERCIAL)
    assertThat(upcoming.startsAt).isEqualTo(dateTime(2024, 1, 8, 7, 0)) // Monday
  }

  // ---------------------------------------------------------------------------
  // Metered with no time limit (timeLimitMinutes = 0): active path
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate returns ActiveTimed with window end expiry for metered with no time limit`() {
    // Metered(0) means "pay but no cap." timeLimitMinutes returns null via takeIf { it > 0 }.
    // evaluateTimedInterval falls through to the limitMinutes == null branch, returning
    // ActiveTimed with expiry = windowEnd.
    val now = dateTime(2024, 1, 1, 10, 0) // Monday 10 AM
    val noLimitMetered =
      ParkingInterval(
        type = IntervalType.Metered(timeLimitMinutes = 0),
        days = weekdays,
        startTime = LocalTime(9, 0),
        endTime = LocalTime(18, 0),
        source = IntervalSource.METER,
      )
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(noLimitMetered))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    assertThat(active.paymentRequired).isTrue()
    // Expiry is the window end (6 PM), not now + some limit
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 18, 0))
  }

  // ---------------------------------------------------------------------------
  // Tow-away priority over meter in same timeline
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate returns Forbidden when tow-away interval is active even if meter also present`() {
    val now = dateTime(2024, 1, 1, 8, 0) // Monday 8 AM
    // Timeline has tow-away 7-9am and meter 9-6pm. At 8am, tow-away should match first
    // because isActiveAt will match the tow-away interval.
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(towAway, weekdayMetered))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.Forbidden::class.java)
    assertThat((state as ParkingRestrictionState.Forbidden).reason)
      .isEqualTo(ProhibitionReason.TOW_AWAY)
  }

  // ---------------------------------------------------------------------------
  // 30-minute limit
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate returns ActiveTimed with 30 minute limit`() {
    val now = dateTime(2024, 1, 1, 12, 0)
    val interval =
      ParkingInterval(
        type = IntervalType.Limited(timeLimitMinutes = 30),
        days = setOf(DayOfWeek.MONDAY),
        startTime = LocalTime(8, 0),
        endTime = LocalTime(18, 0),
        source = IntervalSource.REGULATION,
      )
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(interval))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 12, 30))
  }

  // ---------------------------------------------------------------------------
  // Commercial zones and permit exemptions
  // ---------------------------------------------------------------------------

  @Test
  fun `commercial zone is NOT permit-exempt even if exemptPermitZones is populated`() {
    val now = dateTime(2024, 1, 1, 10, 0) // Monday 10 AM
    val commercial =
      ParkingInterval(
        type = IntervalType.Restricted(ProhibitionReason.COMMERCIAL),
        days = weekdays,
        startTime = LocalTime(7, 0),
        endTime = LocalTime(22, 0),
        exemptPermitZones = listOf("Y"),
        source = IntervalSource.REGULATION,
      )
    val spot = createSpot(id = "1", zone = "Y", limitMinutes = null, timeline = listOf(commercial))

    val state = ParkingRestrictionEvaluator.evaluate(spot, "Y", now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.Forbidden::class.java)
    val forbidden = state as ParkingRestrictionState.Forbidden
    assertThat(forbidden.reason).isEqualTo(ProhibitionReason.COMMERCIAL)
  }

  @Test
  fun `loading zone is NOT permit-exempt`() {
    val now = dateTime(2024, 1, 1, 10, 0)
    val loading =
      ParkingInterval(
        type = IntervalType.Restricted(ProhibitionReason.LOADING_ZONE),
        days = weekdays,
        startTime = LocalTime(7, 0),
        endTime = LocalTime(18, 0),
        exemptPermitZones = listOf("A"),
        source = IntervalSource.REGULATION,
      )
    val spot = createSpot(id = "1", zone = "A", limitMinutes = null, timeline = listOf(loading))

    val state = ParkingRestrictionEvaluator.evaluate(spot, "A", now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.Forbidden::class.java)
  }

  @Test
  fun `RPP-only zone IS permit-exempt`() {
    val now = dateTime(2024, 1, 1, 10, 0)
    val rppOnly =
      ParkingInterval(
        type = IntervalType.Restricted(ProhibitionReason.RESIDENTIAL_PERMIT),
        days = weekdays,
        startTime = LocalTime(8, 0),
        endTime = LocalTime(18, 0),
        exemptPermitZones = listOf("N"),
        source = IntervalSource.REGULATION,
      )
    val spot = createSpot(id = "1", zone = "N", limitMinutes = null, timeline = listOf(rppOnly))

    val state = ParkingRestrictionEvaluator.evaluate(spot, "N", now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.PermitSafe::class.java)
  }

  @Test
  fun `limited interval with permit zone remains permit-exempt`() {
    val now = dateTime(2024, 1, 1, 10, 0)
    val spot =
      createSpot(id = "1", zone = "A", limitMinutes = null, timeline = listOf(weekdayLimited))

    val state = ParkingRestrictionEvaluator.evaluate(spot, "A", now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.PermitSafe::class.java)
  }

  // ---------------------------------------------------------------------------
  // Browse mode (parkedAt == now): expiry should be now + timeLimit, not windowEnd
  // ---------------------------------------------------------------------------

  @Test
  fun `browse mode expiry is now plus time limit, not window end`() {
    // At 2 PM, 2hr limit. Browse mode passes parkedAt == now.
    // Expiry should be 4 PM (now + 120 min), NOT 6 PM (window end).
    val now = dateTime(2024, 1, 1, 14, 0) // Monday 2 PM
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(weekdayLimited))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 16, 0))
  }

  @Test
  fun `browse mode near window end does not clamp to window end`() {
    // At 5:30 PM, 2hr limit, window ends 6 PM. parkedAt == now.
    // Raw expiry = 5:30 + 120 min = 7:30 PM. The limit extends past the window
    // because enforcement doesn't stop mid-limit. Only a Forbidden interval would clamp it.
    val now = dateTime(2024, 1, 1, 17, 30) // Monday 5:30 PM
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(weekdayLimited))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    // 5:30 PM + 120 min = 7:30 PM
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 19, 30))
  }

  // ---------------------------------------------------------------------------
  // Sweeping takes priority: CleaningActive short-circuits even with active forbidden
  // ---------------------------------------------------------------------------

  @Test
  fun `sweeping returns CleaningActive even when forbidden interval is simultaneously active`() {
    val now = dateTime(2024, 1, 1, 8, 30) // Monday, during both sweeping (8-10) and tow (7-9)
    val sweeping = createSweepingSchedule(Weekday.Mon, fromHour = 8, toHour = 10)
    val spot =
      createSpot(id = "1", sweepingSchedules = listOf(sweeping), timeline = listOf(towAway))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    // Must be CleaningActive, NOT Forbidden. Sweeping takes priority.
    assertThat(state).isInstanceOf(ParkingRestrictionState.CleaningActive::class.java)
    val cleaning = state as ParkingRestrictionState.CleaningActive
    assertThat(cleaning.cleaningEnd).isEqualTo(dateTime(2024, 1, 1, 10, 0))
  }

  @Test
  fun `sweeping returns CleaningActive even when metered interval is simultaneously active`() {
    val now = dateTime(2024, 1, 1, 9, 30) // Monday, during both sweeping (8-10) and metered (9-18)
    val sweeping = createSweepingSchedule(Weekday.Mon, fromHour = 8, toHour = 10)
    val spot =
      createSpot(id = "1", sweepingSchedules = listOf(sweeping), timeline = listOf(weekdayMetered))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    // Must be CleaningActive, NOT ActiveTimed
    assertThat(state).isInstanceOf(ParkingRestrictionState.CleaningActive::class.java)
  }

  // ---------------------------------------------------------------------------
  // Restricted interval clamping (new behavior: clamp to Restricted, not just Forbidden)
  // ---------------------------------------------------------------------------

  @Test
  fun `evaluate clamps expiry to next restricted interval start`() {
    // Parked at 10 AM. 2hr limit. Commercial zone starts at 11 AM.
    // Expiry should be 11 AM (clamped), not noon.
    val now = dateTime(2024, 1, 1, 10, 0)
    val limited =
      ParkingInterval(
        type = IntervalType.Limited(timeLimitMinutes = 120),
        days = setOf(DayOfWeek.MONDAY),
        startTime = LocalTime(8, 0),
        endTime = LocalTime(18, 0),
        source = IntervalSource.REGULATION,
      )
    val commercial =
      ParkingInterval(
        type = IntervalType.Restricted(ProhibitionReason.COMMERCIAL),
        days = setOf(DayOfWeek.MONDAY),
        startTime = LocalTime(11, 0),
        endTime = LocalTime(14, 0),
        source = IntervalSource.REGULATION,
      )
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(limited, commercial))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 11, 0))
  }

  // ---------------------------------------------------------------------------
  // Overnight interval: windowStart must use yesterday's date
  // ---------------------------------------------------------------------------

  @Test
  fun `overnight interval returns ActiveTimed at 1 AM, not PendingTimed`() {
    // Overnight limited: 10 PM - 6 AM, Mon-Fri, 2hr limit.
    // At 1 AM Tuesday, the user is inside the window (opened Mon 10 PM).
    // parkedAt = midnight (they parked at midnight).
    val overnight =
      ParkingInterval(
        type = IntervalType.Limited(timeLimitMinutes = 120),
        days = weekdays,
        startTime = LocalTime(22, 0),
        endTime = LocalTime(6, 0),
        source = IntervalSource.REGULATION,
      )
    val parkedAt = dateTime(2024, 1, 2, 0, 0) // Tuesday midnight
    val now = dateTime(2024, 1, 2, 1, 0) // Tuesday 1 AM
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(overnight))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, parkedAt, now, zone)

    // Must be ActiveTimed (inside the window), NOT PendingTimed
    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    // parkedAt midnight + 120 min = 2 AM
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 2, 2, 0))
  }

  @Test
  fun `overnight interval windowStart uses yesterday when parked before window opened`() {
    // Parked at 9 PM Monday (before 10 PM window). Now 1 AM Tuesday.
    // Effective start = max(9 PM, 10 PM yesterday) = 10 PM Monday.
    // Expiry = 10 PM + 120 min = midnight.
    val overnight =
      ParkingInterval(
        type = IntervalType.Limited(timeLimitMinutes = 120),
        days = weekdays,
        startTime = LocalTime(22, 0),
        endTime = LocalTime(6, 0),
        source = IntervalSource.REGULATION,
      )
    val parkedAt = dateTime(2024, 1, 1, 21, 0) // Monday 9 PM
    val now = dateTime(2024, 1, 2, 1, 0) // Tuesday 1 AM
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(overnight))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, parkedAt, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    // windowStart = Mon 10 PM, effectiveStart = max(9 PM, 10 PM) = 10 PM. 10 PM + 120 = midnight.
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 2, 0, 0))
  }

  // ---------------------------------------------------------------------------
  // PendingTimed expiry clamped to next prohibited interval
  // ---------------------------------------------------------------------------

  @Test
  fun `pending timed expiry is clamped by next forbidden interval`() {
    // Saturday noon. Limited M-F 8-6 (3hr limit), tow M-F 10-11 AM.
    // Next limited starts Monday 8 AM. Raw expiry = 8 + 180 = 11 AM. Clamped by tow at 10 AM.
    val now = dateTime(2024, 1, 6, 12, 0) // Saturday noon
    val limited =
      ParkingInterval(
        type = IntervalType.Limited(timeLimitMinutes = 180),
        days = weekdays,
        startTime = LocalTime(8, 0),
        endTime = LocalTime(18, 0),
        source = IntervalSource.REGULATION,
      )
    val forbidden =
      ParkingInterval(
        type = IntervalType.Forbidden(ProhibitionReason.TOW_AWAY),
        days = weekdays,
        startTime = LocalTime(10, 0),
        endTime = LocalTime(11, 0),
        source = IntervalSource.TOW,
      )
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(limited, forbidden))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    // Next limited starts Monday 8 AM -> PendingTimed
    assertThat(state).isInstanceOf(ParkingRestrictionState.PendingTimed::class.java)
    val pending = state as ParkingRestrictionState.PendingTimed
    // Raw expiry = Mon 8 AM + 180 min = 11 AM. Clamped by tow at 10 AM.
    assertThat(pending.expiry).isEqualTo(dateTime(2024, 1, 8, 10, 0)) // Monday
  }

  // ---------------------------------------------------------------------------
  // Permit exemption on upcoming (pending) intervals
  // ---------------------------------------------------------------------------

  @Test
  fun `upcoming limited interval is PermitSafe when user has matching permit`() {
    // Monday 8 PM, outside 8 AM-6 PM window. Next interval: limited with exempt zone "A".
    // User has permit "A". Should be PermitSafe, not PendingTimed.
    val now = dateTime(2024, 1, 1, 20, 0) // Monday 8 PM
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(weekdayLimited))

    val state = ParkingRestrictionEvaluator.evaluate(spot, "A", now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.PermitSafe::class.java)
  }

  @Test
  fun `upcoming metered interval is PermitSafe when user has matching permit`() {
    val now = dateTime(2024, 1, 1, 20, 0) // Monday 8 PM
    val metered = weekdayMetered.copy(exemptPermitZones = listOf("B"))
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(metered))

    val state = ParkingRestrictionEvaluator.evaluate(spot, "B", now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.PermitSafe::class.java)
  }

  @Test
  fun `upcoming limited interval is PendingTimed when permit does not match`() {
    val now = dateTime(2024, 1, 1, 20, 0) // Monday 8 PM
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(weekdayLimited))

    val state = ParkingRestrictionEvaluator.evaluate(spot, "Z", now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.PendingTimed::class.java)
  }

  @Test
  fun `upcoming commercial zone is NOT permit-exempt even with matching zone`() {
    // Saturday noon. Commercial M-F 7-10 PM with exempt zone "Y".
    // User has permit "Y". Should still be ForbiddenUpcoming, not Unrestricted.
    val now = dateTime(2024, 1, 6, 12, 0) // Saturday noon
    val commercial =
      ParkingInterval(
        type = IntervalType.Restricted(ProhibitionReason.COMMERCIAL),
        days = weekdays,
        startTime = LocalTime(7, 0),
        endTime = LocalTime(22, 0),
        exemptPermitZones = listOf("Y"),
        source = IntervalSource.REGULATION,
      )
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(commercial))

    val state = ParkingRestrictionEvaluator.evaluate(spot, "Y", now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ForbiddenUpcoming::class.java)
  }

  @Test
  fun `upcoming RPP-only zone IS permit-exempt`() {
    val now = dateTime(2024, 1, 6, 12, 0) // Saturday noon
    val rppOnly =
      ParkingInterval(
        type = IntervalType.Restricted(ProhibitionReason.RESIDENTIAL_PERMIT),
        days = weekdays,
        startTime = LocalTime(8, 0),
        endTime = LocalTime(18, 0),
        exemptPermitZones = listOf("N"),
        source = IntervalSource.REGULATION,
      )
    val spot = createSpot(id = "1", zone = "N", limitMinutes = null, timeline = listOf(rppOnly))

    val state = ParkingRestrictionEvaluator.evaluate(spot, "N", now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.PermitSafe::class.java)
  }

  // ---------------------------------------------------------------------------
  // Metered with no time limit (timeLimitMinutes = 0) in pending path
  // ---------------------------------------------------------------------------

  @Test
  fun `upcoming metered with no time limit returns PendingTimed, not Unrestricted`() {
    val now = dateTime(2024, 1, 1, 20, 0) // Monday 8 PM, outside 9-6 window
    val noLimitMetered =
      ParkingInterval(
        type = IntervalType.Metered(timeLimitMinutes = 0),
        days = weekdays,
        startTime = LocalTime(9, 0),
        endTime = LocalTime(18, 0),
        source = IntervalSource.METER,
      )
    val spot = createSpot(id = "1", limitMinutes = null, timeline = listOf(noLimitMetered))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.PendingTimed::class.java)
    val pending = state as ParkingRestrictionState.PendingTimed
    assertThat(pending.paymentRequired).isTrue()
  }

  // ---------------------------------------------------------------------------
  // Overnight cleaning with toHour=0 (midnight)
  // ---------------------------------------------------------------------------

  @Test
  fun `overnight cleaning ending at midnight computes cleaningEnd correctly`() {
    val now = dateTime(2024, 1, 1, 23, 30) // Monday 11:30 PM
    val sweeping = createSweepingSchedule(Weekday.Mon, fromHour = 22, toHour = 0)
    val spot = createSpot(id = "1", sweepingSchedules = listOf(sweeping))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now, zone)

    assertThat(state).isInstanceOf(ParkingRestrictionState.CleaningActive::class.java)
    val cleaning = state as ParkingRestrictionState.CleaningActive
    // cleaningEnd should be midnight TONIGHT (start of Tuesday), not midnight this morning
    assertThat(cleaning.cleaningEnd).isEqualTo(dateTime(2024, 1, 2, 0, 0))
  }
}
