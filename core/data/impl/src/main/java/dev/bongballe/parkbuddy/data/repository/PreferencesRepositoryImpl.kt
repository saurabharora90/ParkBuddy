package dev.bongballe.parkbuddy.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.bongballe.parkbuddy.model.ParkedLocation
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class PreferencesRepositoryImpl(private val context: Context, private val json: Json) :
  PreferencesRepository {

  private object Keys {
    val INITIAL_SYNC_DONE = booleanPreferencesKey("initial_sync_done")
    val BLUETOOTH_DEVICE_ADDRESS = stringPreferencesKey("bluetooth_device_address")
    val PARKED_LOCATION = stringPreferencesKey("parked_location")
    val AUTO_TRACKING_ENABLED = booleanPreferencesKey("auto_tracking_enabled")
  }

  override val isInitialSyncDone: Flow<Boolean> =
    context.dataStore.data.map { preferences -> preferences[Keys.INITIAL_SYNC_DONE] ?: false }

  override suspend fun setInitialSyncDone(isDone: Boolean) {
    context.dataStore.edit { preferences -> preferences[Keys.INITIAL_SYNC_DONE] = isDone }
  }

  override val bluetoothDeviceAddress: Flow<String?> =
    context.dataStore.data.map { preferences -> preferences[Keys.BLUETOOTH_DEVICE_ADDRESS] }

  override suspend fun setBluetoothDeviceAddress(address: String?) {
    context.dataStore.edit { preferences ->
      if (address != null) {
        preferences[Keys.BLUETOOTH_DEVICE_ADDRESS] = address
      } else {
        preferences.remove(Keys.BLUETOOTH_DEVICE_ADDRESS)
      }
    }
  }

  override val parkedLocation: Flow<ParkedLocation?> =
    context.dataStore.data.map { preferences ->
      preferences[Keys.PARKED_LOCATION]?.let { jsonString ->
        json.decodeFromString<ParkedLocation>(jsonString)
      }
    }

  override suspend fun setParkedLocation(location: ParkedLocation) {
    val jsonString = json.encodeToString(ParkedLocation.serializer(), location)
    context.dataStore.edit { preferences -> preferences[Keys.PARKED_LOCATION] = jsonString }
  }

  override suspend fun clearParkedLocation() {
    context.dataStore.edit { preferences -> preferences.remove(Keys.PARKED_LOCATION) }
  }

  override val isAutoTrackingEnabled: Flow<Boolean> =
    context.dataStore.data.map { preferences -> preferences[Keys.AUTO_TRACKING_ENABLED] ?: true }

  override suspend fun setAutoTrackingEnabled(enabled: Boolean) {
    context.dataStore.edit { preferences -> preferences[Keys.AUTO_TRACKING_ENABLED] = enabled }
  }
}
