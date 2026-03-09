package dev.bongballe.parkbuddy.data.sf.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import dev.bongballe.parkbuddy.data.sf.database.entity.MeterScheduleEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.ParkingSpotEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.SweepingScheduleEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.UserPreferencesEntity

@Database(
  entities =
    [
      ParkingSpotEntity::class,
      SweepingScheduleEntity::class,
      UserPreferencesEntity::class,
      MeterScheduleEntity::class,
    ],
  version = 1,
)
@TypeConverters(ParkBuddyTypeConverters::class)
@ConstructedBy(ParkBuddyDatabaseConstructor::class)
abstract class ParkBuddyDatabase : RoomDatabase() {
  abstract fun parkingDao(): ParkingDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ParkBuddyDatabaseConstructor : RoomDatabaseConstructor<ParkBuddyDatabase>
