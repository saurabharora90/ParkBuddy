package dev.bongballe.parkbuddy.testing

import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.bongballe.parkbuddy.model.ParkedLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePreferencesRepository : PreferencesRepository {
  private val _isInitialSyncDone = MutableStateFlow(false)
  override val isInitialSyncDone: Flow<Boolean> = _isInitialSyncDone.asStateFlow()

  private val _hasSeenOnboarding = MutableStateFlow(false)
  override val hasSeenOnboarding: Flow<Boolean> = _hasSeenOnboarding.asStateFlow()

  override suspend fun setHasSeenOnboarding(hasSeen: Boolean) {
    _hasSeenOnboarding.value = hasSeen
  }

  private val _hasSeenMapNux = MutableStateFlow(false)
  override val hasSeenMapNux: Flow<Boolean> = _hasSeenMapNux.asStateFlow()

  override suspend fun setHasSeenMapNux(hasSeen: Boolean) {
    _hasSeenMapNux.value = hasSeen
  }

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

  private val _hasSeenZoneNudge = MutableStateFlow(false)
  override val hasSeenZoneNudge = _hasSeenZoneNudge.asStateFlow()

  override suspend fun setHasSeenZoneNudge(hasSeen: Boolean) {
    _hasSeenZoneNudge.value = hasSeen
  }
}
