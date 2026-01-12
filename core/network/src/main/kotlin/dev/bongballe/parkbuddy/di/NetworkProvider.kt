package dev.bongballe.parkbuddy.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@ContributesTo(AppScope::class)
interface NetworkProvider {

  @Provides
  fun providesOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

  @Provides
  fun provideJson(): Json {
    return Json { ignoreUnknownKeys = true }
  }
}
