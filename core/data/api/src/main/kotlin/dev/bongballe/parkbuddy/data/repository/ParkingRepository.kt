package dev.bongballe.parkbuddy.data.repository

import dev.bongballe.parkbuddy.model.ParkingSpot
import kotlinx.coroutines.flow.Flow

interface ParkingRepository {
  fun getAllSpots(): Flow<List<ParkingSpot>>

  fun getSpotsByZone(zone: String): Flow<List<ParkingSpot>>

  fun countSpotsByZone(zone: String): Flow<Int>

  fun getAllRppZones(): Flow<List<String>>

  fun getUserRppZone(): Flow<String?>

  fun getReminders(): Flow<List<Int>>

  suspend fun setUserRppZone(zone: String?)

  suspend fun addReminder(minutesBefore: Int)

  suspend fun removeReminder(minutesBefore: Int)

  suspend fun refreshData(): Boolean
}
