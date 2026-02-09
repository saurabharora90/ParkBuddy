package dev.parkbuddy.feature.reminders.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.data.repository.ReminderRepository
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ReminderMinutes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@ContributesIntoMap(AppScope::class)
@ViewModelKey(WatchlistViewModel::class)
@Inject
class WatchlistViewModel(
  private val repository: ParkingRepository,
  private val reminderRepository: ReminderRepository,
) : ViewModel() {

  val availableZones: StateFlow<List<String>> =
    repository
      .getAllPermitZones()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val selectedZone: StateFlow<String?> =
    repository
      .getUserPermitZone()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
      )

  val watchedSpotCount: StateFlow<Int> =
    selectedZone
      .flatMapLatest { zone -> if (zone == null) flowOf(0) else repository.countSpotsByZone(zone) }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0,
      )

  val watchedSpots: StateFlow<List<ParkingSpot>> =
    selectedZone
      .flatMapLatest { zone ->
        if (zone == null) flowOf(emptyList()) else repository.getSpotsByZone(zone)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val reminders: StateFlow<List<ReminderMinutes>> =
    reminderRepository
      .getReminders()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  private val _isZonePickerExpanded = MutableStateFlow(false)
  val isZonePickerExpanded = _isZonePickerExpanded.asStateFlow()

  fun setZonePickerExpanded(expanded: Boolean) {
    _isZonePickerExpanded.value = expanded
  }

  fun selectZone(zone: String?) {
    viewModelScope.launch {
      repository.setUserPermitZone(zone)
      if (zone != null && reminders.value.isEmpty()) {
        reminderRepository.addReminder(ReminderMinutes(60))
        reminderRepository.addReminder(ReminderMinutes(24 * 60))
      }
    }
    _isZonePickerExpanded.value = false
  }

  fun addReminder(hours: Int, minutes: Int) {
    viewModelScope.launch {
      val totalMinutes = hours * 60 + minutes
      if (totalMinutes > 0) {
        reminderRepository.addReminder(ReminderMinutes(totalMinutes))
      }
    }
  }

  fun removeReminder(reminder: ReminderMinutes) {
    viewModelScope.launch { reminderRepository.removeReminder(reminder) }
  }
}
