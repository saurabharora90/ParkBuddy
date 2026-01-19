package dev.parkbuddy.feature.onboarding.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
  private val preferencesRepository: PreferencesRepository
) : ViewModel() {

  private val _uiState = MutableStateFlow(BluetoothSelectionUiState())
  val uiState: StateFlow<BluetoothSelectionUiState> = _uiState.asStateFlow()

  @SuppressLint("MissingPermission") // Permissions should be handled before calling this
  fun loadPairedDevices(context: Context) {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter = bluetoothManager?.adapter

    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
      // Handle case where Bluetooth is not available or enabled
      _uiState.update { it.copy(devices = emptyList()) }
      return
    }

    val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices ?: emptySet()
    
    _uiState.update { 
        it.copy(devices = pairedDevices.map { device ->
            BluetoothDeviceUiModel(
                name = device.name ?: "Unknown Device",
                address = device.address
            )
        }) 
    }
  }

  fun selectDevice(device: BluetoothDeviceUiModel) {
    _uiState.update { it.copy(selectedDevice = device) }
    viewModelScope.launch {
        preferencesRepository.setBluetoothDeviceAddress(device.address)
    }
  }
}

data class BluetoothSelectionUiState(
  val devices: List<BluetoothDeviceUiModel> = emptyList(),
  val selectedDevice: BluetoothDeviceUiModel? = null
)

data class BluetoothDeviceUiModel(
    val name: String,
    val address: String
)
