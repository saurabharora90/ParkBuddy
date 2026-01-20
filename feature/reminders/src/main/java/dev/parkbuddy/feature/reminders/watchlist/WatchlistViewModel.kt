package dev.parkbuddy.feature.reminders.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.data.repository.RemindersRepository
import dev.bongballe.parkbuddy.data.repository.StreetCleaningRepository
import dev.bongballe.parkbuddy.model.StreetCleaningSegmentModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@ContributesIntoMap(AppScope::class)
@ViewModelKey(WatchlistViewModel::class)
@Inject
class WatchlistViewModel(
  private val repository: StreetCleaningRepository,
  private val remindersRepository: RemindersRepository,
) : ViewModel() {

  val watchedSegments: StateFlow<List<StreetCleaningSegmentModel>> =
    repository
      .getWatchedSegments()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val reminders: StateFlow<List<Int>> =
    remindersRepository
      .getReminders()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(), // Default/Initial
      )

  private val _searchQuery = MutableStateFlow("")
  val searchQuery = _searchQuery.asStateFlow()

  private val _isSearchActive = MutableStateFlow(false)
  val isSearchActive = _isSearchActive.asStateFlow()

  val searchResults: StateFlow<List<StreetCleaningSegmentModel>> =
    _searchQuery
      .debounce(300)
      .flatMapLatest { query ->
        if (query.isBlank()) flowOf(emptyList()) else repository.searchSegments(query)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  fun onSearchQueryChanged(query: String) {
    _searchQuery.value = query
  }

  fun onSearchActiveChanged(active: Boolean) {
    _isSearchActive.value = active
    if (!active) {
      _searchQuery.value = ""
    }
  }

  fun watch(segment: StreetCleaningSegmentModel) {
    viewModelScope.launch {
      repository.setWatchStatus(segment.id, true)
      // Check if reminders are empty, if so, add defaults (12h, 4h)
      if (reminders.value.isEmpty()) {
        remindersRepository.addReminder(12 * 60) // 12 hours
        remindersRepository.addReminder(4 * 60) // 4 hours
      }
    }
    onSearchActiveChanged(false)
  }

  fun unwatch(segment: StreetCleaningSegmentModel) {
    viewModelScope.launch { repository.setWatchStatus(segment.id, false) }
  }

  fun addReminder(hours: Int, minutes: Int) {
    viewModelScope.launch {
      val totalMinutes = hours * 60 + minutes
      if (totalMinutes > 0) {
        remindersRepository.addReminder(totalMinutes)
      }
    }
  }

  fun removeReminder(minutes: Int) {
    viewModelScope.launch { remindersRepository.removeReminder(minutes) }
  }
}
