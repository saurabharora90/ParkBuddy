package dev.bongballe.parkbuddy.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class PreferencesRepositoryImpl(private val dataStore: DataStore<Preferences>) :
  PreferencesRepository {

  private object Keys {
    val INITIAL_SYNC_DONE = booleanPreferencesKey("initial_sync_done")
    val BLUETOOTH_DEVICE_ADDRESS = stringPreferencesKey("bluetooth_device_address")
  }

  override val isInitialSyncDone: Flow<Boolean> =
    dataStore.data.map { preferences -> preferences[Keys.INITIAL_SYNC_DONE] ?: false }

  override suspend fun setInitialSyncDone(isDone: Boolean) {
    dataStore.edit { preferences -> preferences[Keys.INITIAL_SYNC_DONE] = isDone }
  }

  override val bluetoothDeviceAddress: Flow<String?> =
    dataStore.data.map { preferences -> preferences[Keys.BLUETOOTH_DEVICE_ADDRESS] }

  override suspend fun setBluetoothDeviceAddress(address: String) {
    dataStore.edit { preferences -> preferences[Keys.BLUETOOTH_DEVICE_ADDRESS] = address }
  }
}
