package dev.parkbuddy.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.data.repository.StreetCleaningRepository
import dev.bongballe.parkbuddy.model.StreetCleaningSegmentModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class)
@ViewModelKey(MapViewModel::class)
@Inject
class MapViewModel(private val repository: StreetCleaningRepository) : ViewModel() {

  val streetCleaningSegments: StateFlow<List<StreetCleaningSegmentModel>> =
    repository
      .getAllSegments()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  fun toggleWatchStatus(segment: StreetCleaningSegmentModel) {
    viewModelScope.launch { repository.setWatchStatus(segment.id, !segment.isWatched) }
  }
}
