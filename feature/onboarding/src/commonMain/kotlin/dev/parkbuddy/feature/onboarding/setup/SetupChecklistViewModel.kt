package dev.parkbuddy.feature.onboarding.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class)
@ViewModelKey(SetupChecklistViewModel::class)
@Inject
class SetupChecklistViewModel(private val preferencesRepository: PreferencesRepository) :
  ViewModel() {

  fun markOnboardingComplete() {
    viewModelScope.launch { preferencesRepository.setHasSeenOnboarding(true) }
  }
}
