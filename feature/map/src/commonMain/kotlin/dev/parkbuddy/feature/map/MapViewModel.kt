package dev.parkbuddy.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.analytics.AnalyticsTracker
import dev.bongballe.parkbuddy.data.repository.ParkingManager
import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.bongballe.parkbuddy.model.ParkedLocation
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.parkbuddy.feature.map.model.MapViewport
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@ContributesIntoMap(AppScope::class)
@ViewModelKey(MapViewModel::class)
@Inject
class MapViewModel(
  private val parkingManager: ParkingManager,
  private val repository: ParkingRepository,
  private val preferencesRepository: PreferencesRepository,
  private val analyticsTracker: AnalyticsTracker,
  @WithDispatcherType(DispatcherType.DEFAULT) private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

  data class State(
    val visibleSpots: List<ParkingSpot>,
    val parkedSpot: ParkingSpot?,
    val parkedLocation: ParkedLocation?,
    val hasSeenMapNux: Boolean,
    val permitZone: String?,
    val hasSeenZoneNudge: Boolean,
  )

  private val viewportState: MutableStateFlow<MapViewport?> = MutableStateFlow(null)

  val stateFlow: StateFlow<State> =
    combine(
        repository.getAllSpots(),
        preferencesRepository.parkedLocation,
        repository.getUserPermitZone(),
        combine(preferencesRepository.hasSeenMapNux, preferencesRepository.hasSeenZoneNudge) {
          nux,
          seenNudge ->
          nux to seenNudge
        },
        viewportState.debounce(100),
      ) { spots, parkedLocation, permitZone, (hasSeenNux, seenNudge), viewport ->
        val visibleSpots =
          if (viewport == null || viewport.zoom < 15f) {
            emptyList()
          } else {
            spots
              .filter { spot ->
                spot.geometry.coordinates.any { point ->
                  viewport.bounds.contains(latitude = point[1], longitude = point[0])
                }
              }
              .filter { it.isParkable && !it.isCommercial }
          }

        val parkedSpot =
          parkedLocation?.let { loc -> spots.firstOrNull { it.objectId == loc.spotId } }

        State(
          visibleSpots = visibleSpots,
          parkedSpot = parkedSpot,
          parkedLocation = parkedLocation,
          hasSeenMapNux = hasSeenNux,
          permitZone = permitZone,
          hasSeenZoneNudge = seenNudge,
        )
      }
      .flowOn(defaultDispatcher)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue =
          State(
            visibleSpots = emptyList(),
            parkedSpot = null,
            parkedLocation = null,
            hasSeenMapNux = true,
            permitZone = null,
            hasSeenZoneNudge = true,
          ),
      )

  fun markMapNuxSeen() {
    viewModelScope.launch { preferencesRepository.setHasSeenMapNux(true) }
  }

  fun parkHere(spot: ParkingSpot) {
    analyticsTracker.logEvent("manual_park_click")
    viewModelScope.launch { parkingManager.parkHere(spot) }
  }

  fun dismissZoneNudge() {
    viewModelScope.launch { preferencesRepository.setHasSeenZoneNudge(true) }
  }

  fun updateViewport(viewport: MapViewport) {
    viewportState.value = viewport
  }
}
