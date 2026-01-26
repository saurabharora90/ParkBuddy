package dev.parkbuddy.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.bongballe.parkbuddy.model.ParkedLocation
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@ContributesIntoMap(AppScope::class)
@ViewModelKey(MapViewModel::class)
@Inject
class MapViewModel(
  private val repository: ParkingRepository,
  private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

  val parkingSpots: StateFlow<List<ParkingSpot>> =
    repository
      .getAllSpots()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val watchedSpots: StateFlow<List<ParkingSpot>> =
    repository
      .getUserRppZone()
      .flatMapLatest { zone ->
        if (zone == null) flowOf(emptyList()) else repository.getSpotsByZone(zone)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val parkedLocation: StateFlow<ParkedLocation?> =
    preferencesRepository.parkedLocation.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = null,
    )

  fun parkHere(spot: ParkingSpot) {
    viewModelScope.launch {
      val coordinates = spot.geometry.coordinates
      if (coordinates.isEmpty()) return@launch

      val centerLatitude = coordinates.map { it[1] }.average()
      val centerLongitude = coordinates.map { it[0] }.average()

      val parkedLocation =
        ParkedLocation(
          spotId = spot.objectId,
          latitude = centerLatitude,
          longitude = centerLongitude,
          parkedAt = Clock.System.now(),
        )

      preferencesRepository.setParkedLocation(parkedLocation)
    }
  }
}
