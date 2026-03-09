package dev.bongballe.parkbuddy.data.repository.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okio.Path.Companion.toPath

private const val DATASTORE_FILE = "settings.preferences_pb"

@ContributesTo(AppScope::class)
interface DataStoreProvider {

  @Provides
  @SingleIn(AppScope::class)
  fun provideDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
      produceFile = { context.filesDir.resolve("datastore/$DATASTORE_FILE").absolutePath.toPath() }
    )
}
