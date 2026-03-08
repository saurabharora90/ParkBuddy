package dev.bongballe.parkbuddy.model

/**
 * Type of parking regulation.
 *
 * Regulations are categorized as "parkable" or not based on whether general public
 * can legally park there (with appropriate permits/payment).
 *
 * @property displayName Human-readable name for UI display
 * @property isParkable Whether general public can park here (possibly with permit/payment)
 * @property requiresPayment Whether this regulation typically requires payment at a meter
 */
enum class ParkingRegulation(
  val displayName: String,
  val isParkable: Boolean,
  val requiresPayment: Boolean = false,
) {
  /** Standard metered/timed parking (e.g., "2hr limit M-F 8am-6pm") */
  TIME_LIMITED("Time Limited", true),

  /** Either pay the meter OR have a residential permit */
  PAY_OR_PERMIT("Pay or Permit", true, requiresPayment = true),

  /** Must BOTH pay and have permit */
  PAID_PLUS_PERMIT("Paid + Permit", true, requiresPayment = true),

  /** No overnight parking, but OK during day */
  NO_OVERNIGHT("No Overnight", true),

  /** No RVs/large vehicles, regular cars OK */
  NO_OVERSIZED("No Oversized Vehicles", true),

  /** Residential Parking Permit required */
  RPP_ONLY("Residential Permit Only", true),

  /** Standard paid parking via meter */
  METERED("Metered Parking", true, requiresPayment = true),

  /** No parking allowed at any time (red curb, etc.) */
  NO_PARKING("No Parking", false),

  /** No stopping allowed (stricter than no parking) */
  NO_STOPPING("No Stopping", false),

  /** Government vehicles only */
  GOVERNMENT_ONLY("Government Permit Only", false),

  /** No restrictions found (free parking) */
  UNRESTRICTED("Unrestricted", true),

  /** Unrecognized regulation type from API */
  UNKNOWN("Unknown", false),
}
