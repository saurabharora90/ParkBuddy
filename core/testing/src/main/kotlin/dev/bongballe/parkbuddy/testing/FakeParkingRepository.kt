package dev.bongballe.parkbuddy.testing

import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.model.ParkingSpot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeParkingRepository : ParkingRepository {
  private val _spots = MutableStateFlow<List<ParkingSpot>>(emptyList())
  
  override fun getAllSpots(): Flow<List<ParkingSpot>> = _spots

  override fun getSpotsByZone(zone: String): Flow<List<ParkingSpot>> {
    return _spots.map { spots -> spots.filter { it.rppArea == zone } }
  }

  override fun countSpotsByZone(zone: String): Flow<Int> {
    return getSpotsByZone(zone).map { it.size }
  }

  override fun getAllPermitZones(): Flow<List<String>> {
    return _spots.map { spots -> spots.mapNotNull { it.rppArea }.distinct() }
  }

  private val _userPermitZone = MutableStateFlow<String?>(null)
  override fun getUserPermitZone(): Flow<String?> = _userPermitZone

  override suspend fun setUserPermitZone(zone: String?) {
    _userPermitZone.value = zone
  }

  override suspend fun refreshData(): Boolean = true

  fun setSpots(spots: List<ParkingSpot>) {
    _spots.value = spots
  }
}
