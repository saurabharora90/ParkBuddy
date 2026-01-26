package dev.bongballe.parkbuddy.data.sf.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.bongballe.parkbuddy.data.sf.database.entity.ParkingSpotEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.SweepingScheduleEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.UserPreferencesEntity

@Database(
  entities =
    [ParkingSpotEntity::class, SweepingScheduleEntity::class, UserPreferencesEntity::class],
  version = 1,
)
@TypeConverters(ParkBuddyTypeConverters::class)
abstract class ParkBuddyDatabase : RoomDatabase() {
  abstract fun parkingDao(): ParkingDao
}
