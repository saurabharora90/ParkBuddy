package dev.bongballe.parkbuddy.model

import kotlinx.serialization.Serializable

/** Identifies which raw data layer produced a [ParkingInterval]. Useful for debugging. */
@Serializable
enum class IntervalSource {
  SWEEPING,
  METER,
  REGULATION,
  TOW,
}
