package dev.bongballe.parkbuddy.data.repository.utils

import com.google.common.truth.Truth.assertThat
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

  private val weekdayRestriction = TimedRestriction(
    limitHours = 2,
    days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
    startTime = LocalTime(8, 0),
    endTime = LocalTime(18, 0),
  )

  private fun dateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant {
    return LocalDateTime(year, month, day, hour, minute).toInstant(zone)
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
}
