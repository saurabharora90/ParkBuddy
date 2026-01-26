package dev.bongballe.parkbuddy.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.bongballe.parkbuddy.model.ParkedLocation
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class PreferencesRepositoryImpl(private val context: Context, private val json: Json) :
  PreferencesRepository {

  val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

  private object Keys {
    val INITIAL_SYNC_DONE = booleanPreferencesKey("initial_sync_done")
    val BLUETOOTH_DEVICE_ADDRESS = stringPreferencesKey("bluetooth_device_address")
    val PARKED_LOCATION = stringPreferencesKey("parked_location")
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

  override val parkedLocation: Flow<ParkedLocation?> =
    context.dataStore.data.map { preferences ->
      preferences[Keys.PARKED_LOCATION]?.let { jsonString ->
        try {
          json.decodeFromString<ParkedLocation>(jsonString)
        } catch (e: SerializationException) {
          null
        }
      }
    }

  override suspend fun setParkedLocation(location: ParkedLocation) {
    val jsonString = json.encodeToString(ParkedLocation.serializer(), location)
    context.dataStore.edit { preferences -> preferences[Keys.PARKED_LOCATION] = jsonString }
  }

  override suspend fun clearParkedLocation() {
    context.dataStore.edit { preferences -> preferences.remove(Keys.PARKED_LOCATION) }
  }
}
