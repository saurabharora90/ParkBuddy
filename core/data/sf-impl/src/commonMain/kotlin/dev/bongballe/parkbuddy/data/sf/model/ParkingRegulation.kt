package dev.bongballe.parkbuddy.data.sf.model

/**
 * SF-specific parking regulation type, used transiently during data sync.
 *
 * This enum classifies raw API regulation strings (via [toParkingRegulation]) and is used for
 * regulation-ranking when multiple geometries overlap the same CNN. It is NOT stored on the domain
 * [ParkingSpot] model. The actual enforcement rules live in the resolved timeline.
 *
 * @property isParkable Whether general public can park here (possibly with permit/payment)
 * @property requiresPayment Whether this regulation typically requires payment at a meter
 * @property isCommercial Whether this spot is reserved for commercial vehicles
 * @property isShortTerm Whether this spot is for short-term loading only (e.g. Green zones)
 */
enum class ParkingRegulation(
  val isParkable: Boolean,
  val requiresPayment: Boolean = false,
  val isCommercial: Boolean = false,
  val isShortTerm: Boolean = false,
) {
  TIME_LIMITED(true),
  PAY_OR_PERMIT(true, requiresPayment = true),
  PAID_PLUS_PERMIT(true, requiresPayment = true),
  NO_OVERNIGHT(true),
  NO_OVERSIZED(true),
  RPP_ONLY(true),
  METERED(true, requiresPayment = true),
  COMMERCIAL_ONLY(true, requiresPayment = true, isCommercial = true),
  LOADING_ZONE(true, isShortTerm = true),
  NO_PARKING(false),
  NO_STOPPING(false),
  GOVERNMENT_ONLY(false),
  UNKNOWN(false),
}
