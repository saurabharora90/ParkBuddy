package dev.parkbuddy.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.analytics.AnalyticsTracker
import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.bongballe.parkbuddy.data.repository.ReminderRepository
import dev.bongballe.parkbuddy.model.ParkedLocation
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ReminderMinutes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
  private val reminderRepository: ReminderRepository,
  private val analyticsTracker: AnalyticsTracker,
) : ViewModel() {

  data class State(
    val spots: List<ParkingSpot>,
    val watchedSpots: List<ParkingSpot>,
    val parkedLocation: Triple<ParkedLocation, ParkingSpot, List<ReminderMinutes>>?,
    val shouldShowParkedLocationBottomSheet: Boolean,
  )

  private val shouldShowParkedLocationBottomSheet: MutableStateFlow<Boolean> =
    MutableStateFlow(false)

  val stateFlow: StateFlow<State> =
    combine(
        flow = repository.getAllSpots(),
        flow2 =
          repository.getUserRppZone().flatMapLatest { zone ->
            if (zone == null) flowOf(emptyList()) else repository.getSpotsByZone(zone)
          },
        flow3 = preferencesRepository.parkedLocation,
        flow4 = repository.getReminders(),
        flow5 = shouldShowParkedLocationBottomSheet,
        transform = {
          parkingSpots,
          watchedSpots,
          parkedLocation,
          reminder,
          shouldShowParkedLocationBottomSheet ->
          State(
            spots = parkingSpots,
            watchedSpots = watchedSpots,
            parkedLocation =
              parkedLocation?.let {
                val spot = parkingSpots.firstOrNull { it.objectId == parkedLocation.spotId }
                spot?.let { Triple(parkedLocation, it, reminder.sortedDescending()) }
              },
            shouldShowParkedLocationBottomSheet = shouldShowParkedLocationBottomSheet,
          )
        },
      )
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue =
          State(
            spots = emptyList(),
            watchedSpots = emptyList(),
            parkedLocation = null,
            shouldShowParkedLocationBottomSheet = false,
          ),
      )

  init {
    viewModelScope.launch {
      preferencesRepository.parkedLocation.collectLatest {
        shouldShowParkedLocationBottomSheet.value = it != null
      }
    }
  }

  fun parkHere(spot: ParkingSpot) {
    analyticsTracker.logEvent("manual_park_click")
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
      reminderRepository.scheduleReminders(spot)
    }
  }

  fun dismissParkedLocationBottomSheet() {
    shouldShowParkedLocationBottomSheet.value = false
  }

  fun requestParkedLocationBottomSheet() {
    shouldShowParkedLocationBottomSheet.value = true
  }

  fun clearParkedLocation() {
    analyticsTracker.logEvent("clear_parked_location")
    viewModelScope.launch {
      preferencesRepository.clearParkedLocation()
      reminderRepository.clearAllReminders()
    }
  }

  fun reportWrongLocation() {
    analyticsTracker.logEvent("report_wrong_location")
    clearParkedLocation()
  }
}
