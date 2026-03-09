package dev.parkbuddy.feature.onboarding.bluetooth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.analytics.AnalyticsTracker
import dev.bongballe.parkbuddy.core.bluetooth.BluetoothController
import dev.bongballe.parkbuddy.core.bluetooth.BluetoothDeviceUiModel
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
@ViewModelKey(BluetoothDeviceSelectionViewModel::class)
@Inject
class BluetoothDeviceSelectionViewModel(
  private val preferencesRepository: PreferencesRepository,
  private val bluetoothController: BluetoothController,
  private val analyticsTracker: AnalyticsTracker,
) : ViewModel() {

  private val devices by lazy { bluetoothController.getPairedDevices() }
  val uiState: StateFlow<BluetoothSelectionUiState> =
    preferencesRepository.bluetoothDeviceAddress
      .map { deviceAddress ->
        BluetoothSelectionUiState(
          devices = devices,
          selectedDevice = devices.find { it.address == deviceAddress },
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BluetoothSelectionUiState(),
      )

  fun selectDevice(device: BluetoothDeviceUiModel) {
    viewModelScope.launch { preferencesRepository.setBluetoothDeviceAddress(device.address) }
  }

  fun clearDeviceSelection() {
    analyticsTracker.logEvent("bluetooth_device_selection_skipped")
    viewModelScope.launch { preferencesRepository.setBluetoothDeviceAddress(null) }
  }
}

data class BluetoothSelectionUiState(
  val devices: List<BluetoothDeviceUiModel> = emptyList(),
  val selectedDevice: BluetoothDeviceUiModel? = null,
)
