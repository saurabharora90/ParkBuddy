package dev.bongballe.parkbuddy.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class PreferencesRepositoryImpl(private val context: Context) : PreferencesRepository {

  val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

  private object Keys {
    val INITIAL_SYNC_DONE = booleanPreferencesKey("initial_sync_done")
    val BLUETOOTH_DEVICE_ADDRESS = stringPreferencesKey("bluetooth_device_address")
  }

  override val isInitialSyncDone: Flow<Boolean> =
    context.dataStore.data.map { preferences -> preferences[Keys.INITIAL_SYNC_DONE] ?: false }

  override suspend fun setInitialSyncDone(isDone: Boolean) {
    context.dataStore.edit { preferences -> preferences[Keys.INITIAL_SYNC_DONE] = isDone }
  }

  override val bluetoothDeviceAddress: Flow<String?> =
    context.dataStore.data.map { preferences -> preferences[Keys.BLUETOOTH_DEVICE_ADDRESS] }

  override suspend fun setBluetoothDeviceAddress(address: String) {
    context.dataStore.edit { preferences -> preferences[Keys.BLUETOOTH_DEVICE_ADDRESS] = address }
  }
}
