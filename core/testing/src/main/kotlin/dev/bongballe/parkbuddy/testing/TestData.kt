package dev.bongballe.parkbuddy.testing

import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.ParkingRegulation
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.TimedRestriction
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

fun createTestSpot(
  id: String,
  zone: String? = null,
  regulation: ParkingRegulation = ParkingRegulation.RPP_ONLY,
  lat: Double = 37.7749,
  lng: Double = -122.4194,
  side: StreetSide = StreetSide.RIGHT,
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
    regulation = regulation,
    rppArea = zone,
    timedRestriction = timedRestriction,
    sweepingCnn = "123",
    sweepingSide = side,
    sweepingSchedules = emptyList()
  )
}
