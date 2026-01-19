package dev.bongballe.parkbuddy.data.repository

import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
  val isInitialSyncDone: Flow<Boolean>
  suspend fun setInitialSyncDone(isDone: Boolean)

  val bluetoothDeviceAddress: Flow<String?>
  suspend fun setBluetoothDeviceAddress(address: String)
}
