package dev.parkbuddy.core.data.di

import android.content.Context
import androidx.room.Room
import dev.parkbuddy.core.data.database.ParkBuddyDatabase
import dev.parkbuddy.core.data.database.StreetCleaningDao
import dev.parkbuddy.core.data.network.SfOpenDataApi
import dev.parkbuddy.core.data.repository.StreetCleaningRepositoryImpl
import dev.parkbuddy.core.domain.repository.StreetCleaningRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@ContributesTo(AppScope::class)
interface DataProvider {

  @Provides
  fun provideDatabase(context: Context): ParkBuddyDatabase {
    return Room.databaseBuilder(context, ParkBuddyDatabase::class.java, "park_buddy_db").build()
  }

  @Provides
  fun provideDao(database: ParkBuddyDatabase): StreetCleaningDao {
    return database.streetCleaningDao()
  }

  @Provides
  fun provideJson(): Json {
    return Json { ignoreUnknownKeys = true }
  }

  @Provides
  fun provideRetrofit(json: Json): Retrofit {
    return Retrofit.Builder()
      .baseUrl("https://data.sfgov.org/")
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
