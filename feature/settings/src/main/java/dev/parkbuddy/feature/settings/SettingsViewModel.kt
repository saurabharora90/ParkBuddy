package dev.parkbuddy.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.core.bluetooth.BluetoothController
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class)
@ViewModelKey(SettingsViewModel::class)
@Inject
class SettingsViewModel(
  private val preferencesRepository: PreferencesRepository,
  private val bluetoothController: BluetoothController,
) : ViewModel() {

  val isAutoTrackingEnabled: StateFlow<Boolean> =
    preferencesRepository.isAutoTrackingEnabled.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = true,
    )

  val bluetoothDeviceName: StateFlow<String?> =
    preferencesRepository.bluetoothDeviceAddress
      .map { address ->
        address?.let { addr ->
          bluetoothController.getPairedDevices().find { it.address == addr }?.name
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
      )

  fun setAutoTrackingEnabled(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setAutoTrackingEnabled(enabled) }
  }

  fun buyMeACoffee() {}
}
