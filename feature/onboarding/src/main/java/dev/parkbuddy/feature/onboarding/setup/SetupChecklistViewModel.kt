package dev.parkbuddy.feature.onboarding.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class)
@ViewModelKey(SetupChecklistViewModel::class)
@Inject
class SetupChecklistViewModel(private val preferencesRepository: PreferencesRepository) :
  ViewModel() {

  val isSyncDone: StateFlow<Boolean> =
    preferencesRepository.isInitialSyncDone.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = false,
    )

  fun markOnboardingComplete() {
    viewModelScope.launch { preferencesRepository.setHasSeenOnboarding(true) }
  }
}
