package dev.bongballe.parkbuddy.core.navigation

import androidx.navigation3.runtime.NavKey
import dev.bongballe.parkbuddy.model.ParkingSpot
import kotlinx.serialization.Serializable

@Serializable data class SpotDetailRoute(val spot: ParkingSpot, val permitZone: String?) : NavKey
