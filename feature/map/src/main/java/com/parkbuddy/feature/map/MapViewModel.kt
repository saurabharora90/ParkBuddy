package com.parkbuddy.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkbuddy.core.domain.model.StreetCleaningSegmentModel
import com.parkbuddy.core.domain.repository.StreetCleaningRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel @Inject constructor(
    private val repository: StreetCleaningRepository
) : ViewModel() {

    val streetCleaningSegments: StateFlow<List<StreetCleaningSegmentModel>> = repository.getAllSegments()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            repository.refreshData()
        }
    }

    fun toggleWatchStatus(segment: StreetCleaningSegmentModel) {
        viewModelScope.launch {
            repository.setWatchStatus(segment.id, !segment.isWatched)
        }
    }
}