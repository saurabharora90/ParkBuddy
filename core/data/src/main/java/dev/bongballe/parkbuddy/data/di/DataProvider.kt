package dev.bongballe.parkbuddy.data.di

import dev.bongballe.parkbuddy.data.network.SfOpenDataApi
import dev.bongballe.parkbuddy.data.repository.StreetCleaningRepository
import dev.bongballe.parkbuddy.data.repository.StreetCleaningRepositoryImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@ContributesTo(AppScope::class)
interface DataProvider {

  @Provides
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
  fun provideRepository(impl: StreetCleaningRepositoryImpl): StreetCleaningRepository {
    return impl
  }
}
