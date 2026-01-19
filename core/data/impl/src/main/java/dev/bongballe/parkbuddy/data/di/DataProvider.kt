package dev.bongballe.parkbuddy.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.data.network.SfOpenDataApi
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@ContributesTo(AppScope::class)
interface DataProvider {

  @Provides
  @SingleIn(AppScope::class)
  fun provideRetrofit(json: Json, okHttpClient: OkHttpClient): Retrofit {
    return Retrofit.Builder()
      .baseUrl("https://data.sfgov.org/")
      .client(okHttpClient)
      .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
      .build()
  }

  @Provides
  fun provideApi(retrofit: Retrofit): SfOpenDataApi {
    return retrofit.create(SfOpenDataApi::class.java)
  }

  @Provides
  @SingleIn(AppScope::class)
  fun providesDataStore(
    context: Context,
    @WithDispatcherType(DispatcherType.IO) dispatcher: CoroutineDispatcher,
  ): DataStore<Preferences> {
    return PreferenceDataStoreFactory.create(
      corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
      scope = CoroutineScope(dispatcher + SupervisorJob()),
      produceFile = { context.preferencesDataStoreFile("settings") },
    )
  }
}
