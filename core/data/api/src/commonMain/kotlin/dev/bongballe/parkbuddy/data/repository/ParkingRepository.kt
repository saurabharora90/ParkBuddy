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

  /**
   * Builds the DB from local JSON files on disk, falling back to bundled assets. No network calls.
   * Used on first launch for instant startup.
   */
  suspend fun populateDb(): Boolean

  /**
   * Downloads fresh data from APIs, writes JSON to disk, then calls [populateDb]. Used by
   * background workers for periodic updates.
   */
  suspend fun refreshData(): Boolean

  suspend fun hasSpots(): Boolean
}
