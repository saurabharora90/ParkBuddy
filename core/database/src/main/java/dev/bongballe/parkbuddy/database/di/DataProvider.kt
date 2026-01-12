package dev.bongballe.parkbuddy.database.di

import android.content.Context
import androidx.room.Room
import dev.bongballe.parkbuddy.database.ParkBuddyDatabase
import dev.bongballe.parkbuddy.database.StreetCleaningDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

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
}
