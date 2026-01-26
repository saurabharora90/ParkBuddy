package dev.bongballe.parkbuddy.data.sf.di

import com.skydoves.retrofit.adapters.result.ResultCallAdapterFactory
import dev.bongballe.parkbuddy.data.sf.network.SfOpenDataApi
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
interface SfDataProvider {

  @Provides
  @SingleIn(AppScope::class)
  fun provideRetrofit(json: Json, okHttpClient: OkHttpClient): Retrofit {
    return Retrofit.Builder()
      .baseUrl("https://data.sfgov.org/")
      .client(okHttpClient)
      .addCallAdapterFactory(ResultCallAdapterFactory.create())
      .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
      .build()
  }

  @Provides
  fun provideApi(retrofit: Retrofit): SfOpenDataApi {
    return retrofit.create(SfOpenDataApi::class.java)
  }
}
