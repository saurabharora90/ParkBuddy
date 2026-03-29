package dev.bongballe.parkbuddy.data.sf.model

/**
 * SF-specific parking regulation type, used transiently during data sync.
 *
 * This enum classifies raw API regulation strings (via [toParkingRegulation]) and is used for
 * regulation-ranking when multiple geometries overlap the same CNN. It is NOT stored on the domain
 * [ParkingSpot] model. The actual enforcement rules live in the resolved timeline.
 */
enum class ParkingRegulation {
  TIME_LIMITED,
  PAY_OR_PERMIT,
  PAID_PLUS_PERMIT,
  NO_OVERNIGHT,
  NO_OVERSIZED,
  RPP_ONLY,
  METERED,
  COMMERCIAL_ONLY,
  LOADING_ZONE,
  NO_PARKING,
  NO_STOPPING,
  GOVERNMENT_ONLY,
  UNKNOWN,
}
