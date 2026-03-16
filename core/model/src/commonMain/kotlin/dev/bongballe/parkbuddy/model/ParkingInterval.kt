package dev.bongballe.parkbuddy.model

import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * A single contiguous parking rule window in a weekly schedule.
 *
 * The timeline for a parking spot is a `List<ParkingInterval>` sorted by `(days.min, startTime)`.
 * Intervals are non-overlapping within the same day after resolution. Gaps between intervals are
 * implicitly [IntervalType.Open].
 *
 * @property type What kind of parking rule applies (open, limited, metered, restricted, forbidden).
 * @property days Days of the week this interval applies to.
 * @property startTime Start of the enforcement window (inclusive).
 * @property endTime End of the enforcement window (exclusive). If equal to [startTime], this is a
 *   full-day interval.
 * @property exemptPermitZones RPP zones whose holders are exempt from this restriction.
 * @property source Which raw data source produced this interval.
 */
@Serializable
data class ParkingInterval(
  val type: IntervalType,
  val days: Set<DayOfWeek>,
  val startTime: LocalTime,
  val endTime: LocalTime,
  val exemptPermitZones: List<String> = emptyList(),
  val source: IntervalSource,
) {

  /** Whether this interval is active at the given [time]. */
  fun isActiveAt(time: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
    val local = time.toLocalDateTime(zone)
    if (local.dayOfWeek !in days) return false
    val t = local.time

    // Special case: endTime at 23:59 means "rest of day" (from normalizeWindow for 24h meters).
    // Compare only hour:minute so sub-minute precision (23:59:30) doesn't create a gap.
    val isEndOfDay = endTime.hour == 23 && endTime.minute == 59

    return if (startTime <= endTime) {
      if (isEndOfDay) t >= startTime else t in startTime..<endTime
    } else {
      // Overnight window (e.g. 10 PM to 6 AM)
      t !in endTime..<startTime
    }
  }

  /** The time limit in minutes, or null if this interval has no time limit. */
  val timeLimitMinutes: Int?
    get() =
      when (val t = type) {
        is IntervalType.Limited -> t.timeLimitMinutes.takeIf { it > 0 }
        is IntervalType.Metered -> t.timeLimitMinutes.takeIf { it > 0 }
        else -> null
      }

  /** Whether this interval requires payment at a meter. */
  val requiresPayment: Boolean
    get() = type is IntervalType.Metered
}
