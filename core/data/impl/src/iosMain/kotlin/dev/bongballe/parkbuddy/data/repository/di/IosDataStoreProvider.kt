package dev.bongballe.parkbuddy.data.repository.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okio.Path.Companion.toPath
import platform.Foundation.NSHomeDirectory

private const val DATASTORE_FILE = "settings.preferences_pb"

@ContributesTo(AppScope::class)
interface IosDataStoreProvider {

  @Provides
  @SingleIn(AppScope::class)
  fun provideDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
      produceFile = { (NSHomeDirectory() + "/Documents/datastore/$DATASTORE_FILE").toPath() }
    )
}
