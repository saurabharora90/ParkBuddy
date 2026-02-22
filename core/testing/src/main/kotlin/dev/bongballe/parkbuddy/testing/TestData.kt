package dev.bongballe.parkbuddy.testing

import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.ParkingRegulation
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.TimedRestriction
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

fun createTestSpot(
  id: String,
  zone: String? = null,
  lat: Double = 37.7749,
  lng: Double = -122.4194,
  timedRestriction: TimedRestriction? = TimedRestriction(
    limitHours = 2,
    days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
    startTime = null,
    endTime = null,
  ),
): ParkingSpot {
  return ParkingSpot(
    objectId = id,
    geometry = Geometry(
      type = "LineString",
      coordinates = listOf(listOf(lng, lat), listOf(lng + 0.00001, lat + 0.00001))
    ),
    streetName = "Test St",
    blockLimits = "100-200",
    neighborhood = "Test Neighborhood",
    regulation = ParkingRegulation.RPP_ONLY,
    rppArea = zone,
    timedRestriction = timedRestriction,
    sweepingCnn = "123",
    sweepingSide = null,
    sweepingSchedules = emptyList()
  )
}
