package dev.bongballe.parkbuddy.fixtures

import androidx.annotation.VisibleForTesting
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.IntervalSource
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.SweepingSchedule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * Creates a [ParkingSpot] with sensible defaults for tests and previews.
 *
 * If [limitMinutes] is provided and [timeline] is empty, automatically builds a [ParkingInterval]
 * on the timeline so the evaluator can see it.
 */
@VisibleForTesting
fun createSpot(
  id: String,
  zone: String? = null,
  lat: Double = 37.7749,
  lng: Double = -122.4194,
  side: StreetSide = StreetSide.RIGHT,
  limitMinutes: Int? = 120,
  enforcementDays: Set<DayOfWeek>? = DayOfWeek.entries.toSet(),
  enforcementStart: LocalTime? = null,
  enforcementEnd: LocalTime? = null,
  timeline: List<ParkingInterval> = emptyList(),
  sweepingSchedules: List<SweepingSchedule> = emptyList(),
): ParkingSpot {
  val effectiveTimeline =
    if (timeline.isEmpty() && limitMinutes != null && enforcementDays != null) {
      listOf(
        ParkingInterval(
          type = IntervalType.Limited(limitMinutes),
          days = enforcementDays,
          startTime = enforcementStart ?: LocalTime(0, 0),
          endTime = enforcementEnd ?: LocalTime(23, 59),
          exemptPermitZones = zone?.let { listOf(it) }.orEmpty(),
          source = IntervalSource.REGULATION,
        )
      )
    } else {
      timeline
    }

  return ParkingSpot(
    objectId = id,
    geometry =
      Geometry(
        type = "LineString",
        coordinates = listOf(listOf(lng, lat), listOf(lng + 0.00001, lat + 0.00001)),
      ),
    streetName = "Test St",
    blockLimits = "100-200",
    neighborhood = "Test Neighborhood",
    rppAreas = zone?.let { listOf(it) }.orEmpty(),
    sweepingCnn = "123",
    sweepingSide = side,
    sweepingSchedules = sweepingSchedules,
    timeline = effectiveTimeline,
  )
}
