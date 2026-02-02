package dev.bongballe.parkbuddy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class)
@ViewModelKey(MainActivityViewModel::class)
@Inject
class MainActivityViewModel(
  private val repository: ParkingRepository,
  private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

  sealed interface State {
    data object Loading : State

    data object Ready : State
  }

  private val isSyncing = MutableStateFlow(false)

  val stateFlow: StateFlow<State?> =
    isSyncing
      .map { if (it) State.Loading else State.Ready }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
      )

  init {
    viewModelScope.launch {
      // Check if data needs refresh - either first sync or database was wiped
      val needsSync =
        !preferencesRepository.isInitialSyncDone.first() ||
          repository.getAllSpots().first().isEmpty()
      if (needsSync) {
        isSyncing.value = true
        val didRefreshSucceed = repository.refreshData()
        if (didRefreshSucceed) {
          preferencesRepository.setInitialSyncDone(true)
        }
        isSyncing.value = false
      }
    }
  }
}
