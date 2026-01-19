package dev.bongballe.parkbuddy.data.di

import dev.bongballe.parkbuddy.data.network.SfOpenDataApi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
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
}
