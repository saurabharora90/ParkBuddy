package dev.bongballe.parkbuddy.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
class PreferencesRepositoryImpl(
  private val dataStore: DataStore<Preferences>,
  private val json: Json,
) : PreferencesRepository {

  private object Keys {
    val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
    val HAS_SEEN_MAP_NUX = booleanPreferencesKey("has_seen_map_nux")
    val INITIAL_SYNC_DONE = booleanPreferencesKey("initial_sync_done")
    val BLUETOOTH_DEVICE_ADDRESS = stringPreferencesKey("bluetooth_device_address")
    val PARKED_LOCATION = stringPreferencesKey("parked_location")
    val AUTO_TRACKING_ENABLED = booleanPreferencesKey("auto_tracking_enabled")
    val HAS_SEEN_ZONE_NUDGE = booleanPreferencesKey("has_seen_zone_nudge")
  }

  override val isInitialSyncDone: Flow<Boolean> =
    dataStore.data.map { preferences -> preferences[Keys.INITIAL_SYNC_DONE] ?: false }

  override suspend fun setInitialSyncDone(isDone: Boolean) {
    dataStore.edit { preferences -> preferences[Keys.INITIAL_SYNC_DONE] = isDone }
  }

  override val hasSeenOnboarding: Flow<Boolean> =
    dataStore.data.map { preferences -> preferences[Keys.HAS_SEEN_ONBOARDING] ?: false }

  override suspend fun setHasSeenOnboarding(hasSeen: Boolean) {
    dataStore.edit { preferences -> preferences[Keys.HAS_SEEN_ONBOARDING] = hasSeen }
  }

  override val hasSeenMapNux: Flow<Boolean> =
    dataStore.data.map { preferences -> preferences[Keys.HAS_SEEN_MAP_NUX] ?: false }

  override suspend fun setHasSeenMapNux(hasSeen: Boolean) {
    dataStore.edit { preferences -> preferences[Keys.HAS_SEEN_MAP_NUX] = hasSeen }
  }

  override val bluetoothDeviceAddress: Flow<String?> =
    dataStore.data.map { preferences -> preferences[Keys.BLUETOOTH_DEVICE_ADDRESS] }

  override suspend fun setBluetoothDeviceAddress(address: String?) {
    dataStore.edit { preferences ->
      if (address != null) {
        preferences[Keys.BLUETOOTH_DEVICE_ADDRESS] = address
      } else {
        preferences.remove(Keys.BLUETOOTH_DEVICE_ADDRESS)
      }
    }
  }

  override val parkedLocation: Flow<ParkedLocation?> =
    dataStore.data.map { preferences ->
      preferences[Keys.PARKED_LOCATION]?.let { jsonString ->
        json.decodeFromString<ParkedLocation>(jsonString)
      }
    }

  override suspend fun setParkedLocation(location: ParkedLocation) {
    val jsonString = json.encodeToString(ParkedLocation.serializer(), location)
    dataStore.edit { preferences -> preferences[Keys.PARKED_LOCATION] = jsonString }
  }

  override suspend fun clearParkedLocation() {
    dataStore.edit { preferences -> preferences.remove(Keys.PARKED_LOCATION) }
  }

  override val isAutoTrackingEnabled: Flow<Boolean> =
    dataStore.data.map { preferences -> preferences[Keys.AUTO_TRACKING_ENABLED] ?: true }

  override suspend fun setAutoTrackingEnabled(enabled: Boolean) {
    dataStore.edit { preferences -> preferences[Keys.AUTO_TRACKING_ENABLED] = enabled }
  }

  override val hasSeenZoneNudge: Flow<Boolean> =
    dataStore.data.map { preferences -> preferences[Keys.HAS_SEEN_ZONE_NUDGE] ?: false }

  override suspend fun setHasSeenZoneNudge(hasSeen: Boolean) {
    dataStore.edit { preferences -> preferences[Keys.HAS_SEEN_ZONE_NUDGE] = hasSeen }
  }
}
