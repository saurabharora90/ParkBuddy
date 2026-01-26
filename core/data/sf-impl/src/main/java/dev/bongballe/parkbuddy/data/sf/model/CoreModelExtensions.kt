package dev.bongballe.parkbuddy.data.sf.model

import dev.bongballe.parkbuddy.model.ParkingRegulation
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.StreetSide.LEFT
import dev.bongballe.parkbuddy.model.StreetSide.RIGHT

internal fun String.toParkingRegulation(): ParkingRegulation {
  val normalized = trim().lowercase()
  return when {
    normalized.contains("time limited") -> ParkingRegulation.TIME_LIMITED
    normalized == "pay or permit" -> ParkingRegulation.PAY_OR_PERMIT
    normalized == "paid + permit" || normalized == "paid+permit" ->
      ParkingRegulation.PAID_PLUS_PERMIT
    normalized.contains("residential permit") -> ParkingRegulation.RPP_ONLY
    normalized.contains("no overnight") -> ParkingRegulation.NO_OVERNIGHT
    normalized.contains("no oversized") -> ParkingRegulation.NO_OVERSIZED
    normalized.contains("no parking") -> ParkingRegulation.NO_PARKING
    normalized.contains("no stopping") -> ParkingRegulation.NO_STOPPING
    normalized.contains("government permit") -> ParkingRegulation.GOVERNMENT_ONLY
    normalized.contains("limited no parking") -> ParkingRegulation.NO_PARKING
    else -> ParkingRegulation.UNKNOWN
  }
}

/** Converts API value ("L" or "R") to [StreetSide]. Defaults to [LEFT] for unknown values. */
internal fun String.toStreetSide(): StreetSide =
  when (this.uppercase()) {
    "L" -> LEFT
    "R" -> RIGHT
    else -> LEFT
  }
