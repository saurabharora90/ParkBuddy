package dev.bongballe.parkbuddy.data.sf.database.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.data.sf.database.ParkBuddyDatabase
import dev.bongballe.parkbuddy.data.sf.database.ParkingDao
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineDispatcher
import platform.Foundation.NSHomeDirectory

@ContributesTo(AppScope::class)
interface IosDatabaseProvider {

  @Provides
  fun provideDatabase(
    @WithDispatcherType(DispatcherType.IO) ioDispatcher: CoroutineDispatcher
  ): ParkBuddyDatabase {
    val dbPath = NSHomeDirectory() + "/Documents/park_buddy_db"
    return Room.databaseBuilder<ParkBuddyDatabase>(name = dbPath)
      .setDriver(BundledSQLiteDriver())
      .setQueryCoroutineContext(ioDispatcher)
      .fallbackToDestructiveMigration(true)
      .build()
  }

  @Provides
  fun provideParkingDao(database: ParkBuddyDatabase): ParkingDao {
    return database.parkingDao()
  }
}
