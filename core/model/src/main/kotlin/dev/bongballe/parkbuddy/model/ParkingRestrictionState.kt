package dev.bongballe.parkbuddy.model

import kotlin.time.Instant

/**
 * Represents the current status of parking restrictions for a specific time.
 */
sealed class ParkingRestrictionState {
  /** No restrictions apply (except maybe street cleaning) */
  data class Unrestricted(val nextCleaning: Instant?) : ParkingRestrictionState()

  /** User is in their permit zone, only street cleaning applies */
  data class PermitSafe(val nextCleaning: Instant?) : ParkingRestrictionState()

  /** Time limit is currently active */
  data class ActiveTimed(
    val expiry: Instant,
    val nextCleaning: Instant?
  ) : ParkingRestrictionState()

  /** Time limit will start in the future */
  data class PendingTimed(
    val startsAt: Instant,
    val expiry: Instant,
    val nextCleaning: Instant?
  ) : ParkingRestrictionState()
}
