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
            spots.filter { spot -> spot.isParkable && spot.intersectsViewport(viewport) }
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
/**
 * Checks whether any part of this spot's polyline is visible in the viewport.
 *
 * The old check only tested vertices, so a long street whose vertices are both outside the viewport
 * (but whose line passes through it) would disappear when zoomed in.
 */
private fun ParkingSpot.intersectsViewport(viewport: MapViewport): Boolean {
  val bounds = viewport.bounds
  val coords = geometry.coordinates
  if (coords.isEmpty()) return false

  val first = coords[0]
  if (first.size >= 2 && bounds.contains(latitude = first[1], longitude = first[0])) return true

  for (i in 1 until coords.size) {
    val cur = coords[i]
    if (cur.size < 2) continue
    if (bounds.contains(latitude = cur[1], longitude = cur[0])) return true
    val prev = coords[i - 1]
    if (prev.size < 2) continue
    if (bounds.segmentMayIntersect(prev[1], prev[0], cur[1], cur[0])) return true
  }
  return false
}
