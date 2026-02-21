package dev.bongballe.parkbuddy.testing

import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.bongballe.parkbuddy.model.ParkedLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePreferencesRepository : PreferencesRepository {
  private val _isInitialSyncDone = MutableStateFlow(false)
  override val isInitialSyncDone = _isInitialSyncDone.asStateFlow()

  override suspend fun setInitialSyncDone(isDone: Boolean) {
    _isInitialSyncDone.value = isDone
  }

  private val _bluetoothDeviceAddress = MutableStateFlow<String?>(null)
  override val bluetoothDeviceAddress = _bluetoothDeviceAddress.asStateFlow()

  override suspend fun setBluetoothDeviceAddress(address: String?) {
    _bluetoothDeviceAddress.value = address
  }

  private val _parkedLocation = MutableStateFlow<ParkedLocation?>(null)
  override val parkedLocation = _parkedLocation.asStateFlow()

  override suspend fun setParkedLocation(location: ParkedLocation) {
    _parkedLocation.value = location
  }

  override suspend fun clearParkedLocation() {
    _parkedLocation.value = null
  }

  private val _isAutoTrackingEnabled = MutableStateFlow(false)
  override val isAutoTrackingEnabled = _isAutoTrackingEnabled.asStateFlow()

  override suspend fun setAutoTrackingEnabled(enabled: Boolean) {
    _isAutoTrackingEnabled.value = enabled
  }
}
