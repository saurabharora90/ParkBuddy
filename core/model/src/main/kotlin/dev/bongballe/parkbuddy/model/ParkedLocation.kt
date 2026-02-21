package dev.bongballe.parkbuddy.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class ParkingType {
  PERMIT,
  TIMED,
  UNRESTRICTED
}

@Serializable
data class ParkedLocation(
  val spotId: String,
  val location: Location,
  val parkedAt: Instant,
  val parkingType: ParkingType = ParkingType.PERMIT,
)
