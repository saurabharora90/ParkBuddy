package dev.parkbuddy.feature.onboarding.permission

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@ContributesIntoMap(AppScope::class)
@ViewModelKey(RequestPermissionViewModel::class)
@Inject
class RequestPermissionViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(OnboardingUiState())
  val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

  fun updatePermissionState(isFineLocationGranted: Boolean, isBluetoothGranted: Boolean) {
    _uiState.update {
      it.copy(
        isFineLocationGranted = isFineLocationGranted,
        isBluetoothGranted = isBluetoothGranted,
      )
    }
  }

  fun updateBackgroundLocationState(isBackgroundLocationGranted: Boolean) {
    _uiState.update { it.copy(isBackgroundLocationGranted = isBackgroundLocationGranted) }
  }
}

data class OnboardingUiState(
  val isFineLocationGranted: Boolean = false,
  val isBackgroundLocationGranted: Boolean = false,
  val isBluetoothGranted: Boolean = false,
) {
  val areAllPermissionsGranted: Boolean
    get() = isFineLocationGranted && isBackgroundLocationGranted && isBluetoothGranted
}
