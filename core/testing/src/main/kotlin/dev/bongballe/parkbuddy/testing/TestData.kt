package dev.bongballe.parkbuddy.testing

import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.ParkingRegulation
import dev.bongballe.parkbuddy.model.ParkingSpot

fun createTestSpot(
  id: String, 
  zone: String? = null, 
  lat: Double = 37.7749, 
  lng: Double = -122.4194
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
    timeLimitHours = 2,
    enforcementDays = "M-F",
    enforcementStart = null,
    enforcementEnd = null,
    sweepingCnn = "123",
    sweepingSide = null,
    sweepingSchedules = emptyList()
  )
}
