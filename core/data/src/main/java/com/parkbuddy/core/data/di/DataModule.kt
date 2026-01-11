package com.parkbuddy.core.data.di

import android.content.Context
import androidx.room.Room
import com.parkbuddy.core.data.database.ParkBuddyDatabase
import com.parkbuddy.core.data.database.StreetCleaningDao
import com.parkbuddy.core.data.network.SfOpenDataApi
import com.parkbuddy.core.data.repository.StreetCleaningRepositoryImpl
import com.parkbuddy.core.domain.repository.StreetCleaningRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@DependencyGraph
interface DataGraph {
    val streetCleaningRepository: StreetCleaningRepository

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): DataGraph
    }

    @Provides
    fun provideDatabase(context: Context): ParkBuddyDatabase {
        return Room.databaseBuilder(
            context,
            ParkBuddyDatabase::class.java,
            "park_buddy_db"
        ).build()
    }

    @Provides
    fun provideDao(database: ParkBuddyDatabase): StreetCleaningDao {
        return database.streetCleaningDao()
    }

    @Provides
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    fun provideRetrofit(moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://data.sfgov.org/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
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