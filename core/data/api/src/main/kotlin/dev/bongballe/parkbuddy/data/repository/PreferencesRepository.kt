package dev.bongballe.parkbuddy.data.repository

import dev.bongballe.parkbuddy.model.ParkedLocation
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
  val isInitialSyncDone: Flow<Boolean>
  suspend fun setInitialSyncDone(isDone: Boolean)

  val bluetoothDeviceAddress: Flow<String?>
  suspend fun setBluetoothDeviceAddress(address: String)

  val parkedLocation: Flow<ParkedLocation?>
  suspend fun setParkedLocation(location: ParkedLocation)
  suspend fun clearParkedLocation()
}
