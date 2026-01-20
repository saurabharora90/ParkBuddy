package dev.bongballe.parkbuddy.database.di

import android.content.Context
import androidx.room.Room
import dev.bongballe.parkbuddy.database.ParkBuddyDatabase
import dev.bongballe.parkbuddy.database.StreetCleaningDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface DatabaseProvider {

  @Provides
  fun provideDatabase(context: Context): ParkBuddyDatabase {
    return Room.databaseBuilder(context, ParkBuddyDatabase::class.java, "park_buddy_db")
      .fallbackToDestructiveMigration(true)
      .build()
  }

  @Provides
  fun provideDao(database: ParkBuddyDatabase): StreetCleaningDao {
    return database.streetCleaningDao()
  }

  @Provides
  fun provideReminderDao(
    database: ParkBuddyDatabase
  ): dev.bongballe.parkbuddy.database.ReminderDao {
    return database.reminderDao()
  }
}
