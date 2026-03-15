package dev.parkbuddy.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.analytics.AnalyticsTracker
import dev.bongballe.parkbuddy.data.repository.ParkingManager
import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.bongballe.parkbuddy.data.repository.ReminderRepository
import dev.bongballe.parkbuddy.data.repository.utils.ParkingRestrictionEvaluator
import dev.bongballe.parkbuddy.model.ParkedLocation
import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ReminderMinutes
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.parkbuddy.feature.map.model.MapViewport
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@ContributesIntoMap(AppScope::class)
@ViewModelKey(MapViewModel::class)
@Inject
class MapViewModel(
  private val parkingManager: ParkingManager,
  private val repository: ParkingRepository,
  private val preferencesRepository: PreferencesRepository,
  private val reminderRepository: ReminderRepository,
  private val analyticsTracker: AnalyticsTracker,
  @WithDispatcherType(DispatcherType.DEFAULT) private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

  data class ParkedState(
    val parkedLocation: ParkedLocation,
    val spot: ParkingSpot,
    val restrictionState: ParkingRestrictionState,
    val reminders: List<ReminderMinutes>,
  )

  data class State(
    val visibleSpots: List<ParkingSpot>,
    val permitSpots: List<ParkingSpot>,
    val parkedState: ParkedState?,
    val shouldShowParkedLocationBottomSheet: Boolean,
    val hasSeenMapNux: Boolean,
    val hasPermitZone: Boolean,
    val hasSeenZoneNudge: Boolean,
  )

  private val shouldShowParkedLocationBottomSheet: MutableStateFlow<Boolean> =
    MutableStateFlow(false)

  private val viewportState: MutableStateFlow<MapViewport?> = MutableStateFlow(null)

  private val parkedStateFlow: Flow<ParkedState?> =
    preferencesRepository.parkedLocation.flatMapLatest { parkedLocation ->
      if (parkedLocation == null) {
        flowOf(null)
      } else {
        combine(
          repository.getAllSpots(),
          repository.getUserPermitZone(),
          reminderRepository.getReminders(),
          tickerFlow(), // constantly evaluate the current restrictions.
        ) { spots, userPermitZone, reminders, now ->
          val spot =
            spots.firstOrNull { it.objectId == parkedLocation.spotId } ?: return@combine null
          val restrictionState =
            ParkingRestrictionEvaluator.evaluate(
              spot = spot,
              userPermitZone = userPermitZone,
              parkedAt = parkedLocation.parkedAt,
              currentTime = now,
            )
          ParkedState(parkedLocation, spot, restrictionState, reminders.sortedDescending())
        }
      }
    }

  private val permitZoneFlow = repository.getUserPermitZone()

  private val spotsFlow =
    repository.getAllSpots().map { spots -> spots.filter { it.regulation.isParkable } }

  val stateFlow: StateFlow<State> =
    combine(
        combine(
          spotsFlow,
          permitZoneFlow.flatMapLatest { zone ->
            if (zone == null) flowOf(emptyList()) else repository.getSpotsByZone(zone)
          },
          parkedStateFlow,
        ) { spots, permit, parked ->
          Triple(spots, permit, parked)
        },
        combine(
          shouldShowParkedLocationBottomSheet,
          combine(
            preferencesRepository.hasSeenMapNux,
            permitZoneFlow,
            preferencesRepository.hasSeenZoneNudge,
          ) { nux, zone, seenNudge ->
            Triple(nux, zone, seenNudge)
          },
          viewportState.debounce(100),
        ) { sheet, nuxInfo, viewport ->
          Triple(sheet, nuxInfo, viewport)
        },
      ) { mainInfo, extraInfo ->
        val (parkingSpots, permitSpots, parkedState) = mainInfo
        val (shouldShowSheet, nuxInfo, viewport) = extraInfo
        val (hasSeenNux, permitZone, seenNudge) = nuxInfo

        val visibleSpots =
          if (viewport == null || viewport.zoom < 15f) {
            emptyList()
          } else {
            parkingSpots.filter { spot ->
              spot.geometry.coordinates.any { point ->
                viewport.bounds.contains(latitude = point[1], longitude = point[0])
              }
            }
          }

        State(
          visibleSpots = visibleSpots,
          permitSpots = permitSpots,
          parkedState = parkedState,
          shouldShowParkedLocationBottomSheet = shouldShowSheet,
          hasSeenMapNux = hasSeenNux,
          hasPermitZone = permitZone != null,
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
            permitSpots = emptyList(),
            parkedState = null,
            shouldShowParkedLocationBottomSheet = false,
            hasSeenMapNux = true,
            hasPermitZone = true,
            hasSeenZoneNudge = true,
          ),
      )

  init {
    viewModelScope.launch {
      preferencesRepository.parkedLocation.collectLatest {
        shouldShowParkedLocationBottomSheet.value = it != null
      }
    }
  }

  fun markMapNuxSeen() {
    viewModelScope.launch { preferencesRepository.setHasSeenMapNux(true) }
  }

  fun parkHere(spot: ParkingSpot) {
    analyticsTracker.logEvent("manual_park_click")
    viewModelScope.launch { parkingManager.parkHere(spot) }
  }

  fun dismissParkedLocationBottomSheet() {
    shouldShowParkedLocationBottomSheet.value = false
  }

  fun requestParkedLocationBottomSheet() {
    shouldShowParkedLocationBottomSheet.value = true
  }

  fun clearParkedLocation() {
    analyticsTracker.logEvent("clear_parked_location")
    viewModelScope.launch { parkingManager.markCarMoved() }
  }

  fun dismissZoneNudge() {
    viewModelScope.launch { preferencesRepository.setHasSeenZoneNudge(true) }
  }

  fun reportWrongLocation() {
    analyticsTracker.logEvent("report_wrong_location")
    clearParkedLocation()
  }

  fun updateViewport(viewport: MapViewport) {
    viewportState.value = viewport
  }
}

private fun tickerFlow(periodMs: Long = 30_000L): Flow<kotlin.time.Instant> = flow {
  while (true) {
    emit(Clock.System.now())
    delay(periodMs)
  }
}
