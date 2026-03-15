package dev.bongballe.parkbuddy.data.repository.utils

import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.model.MeterSchedule
import dev.bongballe.parkbuddy.model.ParkingRegulation
import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.TimedRestriction
import dev.bongballe.parkbuddy.testing.createTestSpot
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test

class ParkingRestrictionEvaluatorTest {

  private val zone = TimeZone.currentSystemDefault()

  private val weekdayRestriction =
    TimedRestriction(
      limitHours = 2,
      days =
        setOf(
          DayOfWeek.MONDAY,
          DayOfWeek.TUESDAY,
          DayOfWeek.WEDNESDAY,
          DayOfWeek.THURSDAY,
          DayOfWeek.FRIDAY,
        ),
      startTime = LocalTime(8, 0),
      endTime = LocalTime(18, 0),
    )

  private fun dateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant {
    return LocalDateTime(year, month, day, hour, minute).toInstant(zone)
  }

  @Test
  fun `evaluate returns Forbidden when regulation is not parkable`() {
    val now = dateTime(2024, 1, 1, 12, 0)
    val spot = createTestSpot(id = "1", regulation = ParkingRegulation.NO_PARKING)

    val state = ParkingRestrictionEvaluator.evaluate(spot, "A", now, now)

    assertThat(state).isInstanceOf(ParkingRestrictionState.Forbidden::class.java)
    assertThat((state as ParkingRestrictionState.Forbidden).reason).isEqualTo("No Parking")
  }

  @Test
  fun `evaluate returns PermitSafe when spot is in user zone`() {
    val now = dateTime(2024, 1, 1, 12, 0) // Monday
    val spot = createTestSpot(id = "1", zone = "A")

    val state = ParkingRestrictionEvaluator.evaluate(spot, "A", now, now)

    assertThat(state).isInstanceOf(ParkingRestrictionState.PermitSafe::class.java)
  }

  @Test
  fun `evaluate returns Unrestricted when no time limit and not in permit zone`() {
    val now = dateTime(2024, 1, 1, 12, 0)
    val spot = createTestSpot(id = "1", zone = "B", timedRestriction = null)

    val state = ParkingRestrictionEvaluator.evaluate(spot, "A", now, now)

    assertThat(state).isInstanceOf(ParkingRestrictionState.Unrestricted::class.java)
  }

  @Test
  fun `evaluate returns ActiveTimed when parked during enforcement hours`() {
    // Monday 2 PM
    val now = dateTime(2024, 1, 1, 14, 0)
    val spot = createTestSpot(id = "1", timedRestriction = weekdayRestriction)

    val state = ParkingRestrictionEvaluator.evaluate(spot, "B", now, now)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 16, 0))
  }

  @Test
  fun `evaluate returns PendingTimed when parked after enforcement hours`() {
    // Monday 7 PM
    val now = dateTime(2024, 1, 1, 19, 0)
    val spot = createTestSpot(id = "1", timedRestriction = weekdayRestriction)

    val state = ParkingRestrictionEvaluator.evaluate(spot, "B", now, now)

    assertThat(state).isInstanceOf(ParkingRestrictionState.PendingTimed::class.java)
    val pending = state as ParkingRestrictionState.PendingTimed
    assertThat(pending.startsAt).isEqualTo(dateTime(2024, 1, 2, 8, 0)) // Tuesday morning
    assertThat(pending.expiry).isEqualTo(dateTime(2024, 1, 2, 10, 0))
  }

  @Test
  fun `evaluate skips to next window when remaining enforcement is shorter than limit`() {
    // Monday 5:30 PM, enforcement 8am-6pm, 2hr limit
    // Only 30 min left today, can't violate 2hr limit. Skip to tomorrow.
    val now = dateTime(2024, 1, 1, 17, 30)
    val spot = createTestSpot(id = "1", timedRestriction = weekdayRestriction)

    val state = ParkingRestrictionEvaluator.evaluate(spot, "B", now, now)

    assertThat(state).isInstanceOf(ParkingRestrictionState.PendingTimed::class.java)
    val pending = state as ParkingRestrictionState.PendingTimed
    assertThat(pending.startsAt).isEqualTo(dateTime(2024, 1, 2, 8, 0))
    assertThat(pending.expiry).isEqualTo(dateTime(2024, 1, 2, 10, 0))
  }

  @Test
  fun `evaluate returns ActiveTimed when remaining enforcement equals limit exactly`() {
    // Monday 4 PM, enforcement 8am-6pm, 2hr limit
    // Exactly 2hr left, limit can be hit at 6 PM.
    val now = dateTime(2024, 1, 1, 16, 0)
    val spot = createTestSpot(id = "1", timedRestriction = weekdayRestriction)

    val state = ParkingRestrictionEvaluator.evaluate(spot, "B", now, now)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 18, 0))
  }

  @Test
  fun `evaluate returns ActiveTimed when limit fits comfortably in window`() {
    // Monday 10 AM, enforcement 8am-6pm, 2hr limit -> expiry noon
    val now = dateTime(2024, 1, 1, 10, 0)
    val spot = createTestSpot(id = "1", timedRestriction = weekdayRestriction)

    val state = ParkingRestrictionEvaluator.evaluate(spot, "B", now, now)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 12, 0))
  }

  @Test
  fun `evaluate skips to next window on Friday evening for M-F restriction`() {
    // Friday 5:30 PM, enforcement 8am-6pm M-F, 2hr limit
    // 30 min left today, next window is Monday 8 AM
    val now = dateTime(2024, 1, 5, 17, 30) // Friday
    val spot = createTestSpot(id = "1", timedRestriction = weekdayRestriction)

    val state = ParkingRestrictionEvaluator.evaluate(spot, "B", now, now)

    assertThat(state).isInstanceOf(ParkingRestrictionState.PendingTimed::class.java)
    val pending = state as ParkingRestrictionState.PendingTimed
    assertThat(pending.startsAt).isEqualTo(dateTime(2024, 1, 8, 8, 0)) // Monday
    assertThat(pending.expiry).isEqualTo(dateTime(2024, 1, 8, 10, 0))
  }

  @Test
  fun `evaluate does not skip window when no endTime`() {
    val now = dateTime(2024, 1, 1, 22, 0)
    val restriction =
      TimedRestriction(
        limitHours = 2,
        days =
          setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
          ),
        startTime = LocalTime(8, 0),
        endTime = null,
      )
    val spot = createTestSpot(id = "1", timedRestriction = restriction)

    val state = ParkingRestrictionEvaluator.evaluate(spot, "B", now, now)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 2, 0, 0))
  }

  @Test
  fun `evaluate returns PendingTimed when parked on Sunday for M-F spot`() {
    // Sunday Jan 7th 2024, 12 PM
    val now = dateTime(2024, 1, 7, 12, 0)
    val spot = createTestSpot(id = "1", timedRestriction = weekdayRestriction)

    val state = ParkingRestrictionEvaluator.evaluate(spot, "B", now, now)

    assertThat(state).isInstanceOf(ParkingRestrictionState.PendingTimed::class.java)
    val pending = state as ParkingRestrictionState.PendingTimed
    assertThat(pending.startsAt).isEqualTo(dateTime(2024, 1, 8, 8, 0)) // Monday morning
    assertThat(pending.expiry).isEqualTo(dateTime(2024, 1, 8, 10, 0))
  }

  @Test
  fun `evaluate returns Forbidden for metered spot during active Tow window`() {
    val now = dateTime(2024, 1, 1, 8, 0) // Monday 8 AM
    val towSchedule =
      MeterSchedule(
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
        startTime = LocalTime(7, 0),
        endTime = LocalTime(9, 0),
        timeLimitMinutes = 0,
        isTowZone = true,
      )
    val spot =
      createTestSpot(id = "1", regulation = ParkingRegulation.METERED, timedRestriction = null)
        .copy(meterSchedules = listOf(towSchedule))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now)

    assertThat(state).isInstanceOf(ParkingRestrictionState.Forbidden::class.java)
    assertThat((state as ParkingRestrictionState.Forbidden).reason).isEqualTo("Tow Away Zone")
  }

  @Test
  fun `evaluate returns ActiveTimed for metered spot during active operating window`() {
    val now = dateTime(2024, 1, 1, 10, 0) // Monday 10 AM
    val meterSchedule =
      MeterSchedule(
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
        startTime = LocalTime(9, 0),
        endTime = LocalTime(18, 0),
        timeLimitMinutes = 60,
        isTowZone = false,
      )
    val spot =
      createTestSpot(id = "1", regulation = ParkingRegulation.METERED, timedRestriction = null)
        .copy(meterSchedules = listOf(meterSchedule))

    val state = ParkingRestrictionEvaluator.evaluate(spot, null, now, now)

    assertThat(state).isInstanceOf(ParkingRestrictionState.ActiveTimed::class.java)
    val active = state as ParkingRestrictionState.ActiveTimed
    assertThat(active.expiry).isEqualTo(dateTime(2024, 1, 1, 11, 0)) // 10 AM + 60 min
    assertThat(active.paymentRequired).isTrue()
  }

  @Test
  fun `evaluate returns PermitSafe when spot has multiple zones and user has one of them`() {
    val now = dateTime(2024, 1, 1, 12, 0)
    val spot = createTestSpot(id = "1").copy(rppAreas = listOf("A", "B", "C"))

    // User has permit for A
    val stateA = ParkingRestrictionEvaluator.evaluate(spot, "A", now, now)
    assertThat(stateA).isInstanceOf(ParkingRestrictionState.PermitSafe::class.java)

    // User has permit for B
    val stateB = ParkingRestrictionEvaluator.evaluate(spot, "B", now, now)
    assertThat(stateB).isInstanceOf(ParkingRestrictionState.PermitSafe::class.java)

    // User has permit for D (none of the zones)
    val stateD = ParkingRestrictionEvaluator.evaluate(spot, "D", now, now)
    assertThat(stateD).isNotInstanceOf(ParkingRestrictionState.PermitSafe::class.java)
  }
}
