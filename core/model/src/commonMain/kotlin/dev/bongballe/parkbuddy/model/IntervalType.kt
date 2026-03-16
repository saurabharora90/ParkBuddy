package dev.bongballe.parkbuddy.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The type of parking rule active during a [ParkingInterval].
 *
 * Priority hierarchy (highest wins when intervals overlap):
 *
 *     FORBIDDEN (4) > RESTRICTED (3) > METERED (2) > LIMITED (1) > OPEN (0)
 *
 * Within the same priority tier, the shorter time limit wins (safer for the user).
 */
@Serializable
sealed interface IntervalType : Comparable<IntervalType> {
  val priority: Int

  override fun compareTo(other: IntervalType): Int = priority.compareTo(other.priority)

  /** No restrictions. Free parking. */
  @Serializable
  @SerialName("open")
  data object Open : IntervalType {
    override val priority = 0
  }

  /** Free but time-limited (e.g. "2hr M-F 8am-6pm"). */
  @Serializable
  @SerialName("limited")
  data class Limited(val timeLimitMinutes: Int) : IntervalType {
    override val priority = 1
  }

  /** Paid parking with optional time limit. */
  @Serializable
  @SerialName("metered")
  data class Metered(val timeLimitMinutes: Int) : IntervalType {
    override val priority = 2
  }

  /** Restricted to specific vehicle classes (commercial, loading, government). */
  @Serializable
  @SerialName("restricted")
  data class Restricted(val reason: String) : IntervalType {
    override val priority = 3
  }

  /** No parking allowed (tow-away, street cleaning, no-parking signs). */
  @Serializable
  @SerialName("forbidden")
  data class Forbidden(val reason: String) : IntervalType {
    override val priority = 4
  }
}
