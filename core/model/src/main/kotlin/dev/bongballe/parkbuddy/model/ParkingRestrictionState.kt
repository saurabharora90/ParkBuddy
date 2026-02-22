package dev.bongballe.parkbuddy.model

import kotlin.time.Instant

/**
 * Represents the current status of parking restrictions for a specific time.
 */
sealed class ParkingRestrictionState {
  abstract val nextCleaning: Instant?

  /** No restrictions apply (except maybe street cleaning) */
  data class Unrestricted(override val nextCleaning: Instant?) : ParkingRestrictionState()

  /** User is in their permit zone, only street cleaning applies */
  data class PermitSafe(override val nextCleaning: Instant?) : ParkingRestrictionState()

  /** Time limit is currently active */
  data class ActiveTimed(
    val expiry: Instant,
    override val nextCleaning: Instant?
  ) : ParkingRestrictionState()

  /** Time limit will start in the future */
  data class PendingTimed(
    val startsAt: Instant,
    val expiry: Instant,
    override val nextCleaning: Instant?
  ) : ParkingRestrictionState()
}
