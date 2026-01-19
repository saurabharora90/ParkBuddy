package dev.parkbuddy.feature.onboarding.bluetooth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.core.bluetooth.BluetoothController
import dev.bongballe.parkbuddy.core.bluetooth.BluetoothDeviceUiModel
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class)
@ViewModelKey(BluetoothDeviceSelectionViewModel::class)
@Inject
class BluetoothDeviceSelectionViewModel(
  private val preferencesRepository: PreferencesRepository,
  private val bluetoothController: BluetoothController,
) : ViewModel() {

  private val _uiState = MutableStateFlow(BluetoothSelectionUiState())
  val uiState: StateFlow<BluetoothSelectionUiState> = _uiState.asStateFlow()

  fun loadPairedDevices() {
    val devices = bluetoothController.getPairedDevices()
    _uiState.update { it.copy(devices = devices) }
  }

  fun selectDevice(device: BluetoothDeviceUiModel) {
    _uiState.update { it.copy(selectedDevice = device) }
    viewModelScope.launch { preferencesRepository.setBluetoothDeviceAddress(device.address) }
  }
}

data class BluetoothSelectionUiState(
  val devices: List<BluetoothDeviceUiModel> = emptyList(),
  val selectedDevice: BluetoothDeviceUiModel? = null,
)
