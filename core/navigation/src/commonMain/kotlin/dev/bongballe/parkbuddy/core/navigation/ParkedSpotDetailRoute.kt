package dev.bongballe.parkbuddy.core.navigation

import androidx.navigation3.runtime.NavKey
import dev.bongballe.parkbuddy.model.ParkingSpot
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ParkedSpotDetailRoute(
  val spot: ParkingSpot,
  val parkedAt: Instant,
  val permitZone: String?,
) : NavKey
