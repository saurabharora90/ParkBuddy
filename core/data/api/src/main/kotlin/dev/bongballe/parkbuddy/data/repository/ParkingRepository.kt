package dev.bongballe.parkbuddy.data.repository

import dev.bongballe.parkbuddy.model.ParkingSpot
import kotlinx.coroutines.flow.Flow

interface ParkingRepository {
  fun getAllSpots(): Flow<List<ParkingSpot>>

  fun getSpotsByZone(zone: String): Flow<List<ParkingSpot>>

  fun countSpotsByZone(zone: String): Flow<Int>

  fun getAllPermitZones(): Flow<List<String>>

  fun getUserPermitZone(): Flow<String?>

  suspend fun setUserPermitZone(zone: String?)

  suspend fun refreshData(): Boolean
}
