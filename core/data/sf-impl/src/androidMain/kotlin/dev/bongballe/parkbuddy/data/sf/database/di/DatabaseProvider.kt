package dev.bongballe.parkbuddy.data.sf.database.di

import android.content.Context
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

@ContributesTo(AppScope::class)
interface DatabaseProvider {

  @Provides
  fun provideDatabase(
    context: Context,
    @WithDispatcherType(DispatcherType.IO) ioDispatcher: CoroutineDispatcher,
  ): ParkBuddyDatabase {
    return Room.databaseBuilder(context, ParkBuddyDatabase::class.java, "park_buddy_db")
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
