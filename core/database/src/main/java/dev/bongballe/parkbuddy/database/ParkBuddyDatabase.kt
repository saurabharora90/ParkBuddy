package dev.bongballe.parkbuddy.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.bongballe.parkbuddy.database.entity.CleaningScheduleEntity
import dev.bongballe.parkbuddy.database.entity.StreetSegmentEntity
import dev.bongballe.parkbuddy.database.entity.WatchedSegmentEntity

@Database(
  entities =
    [
      StreetSegmentEntity::class,
      CleaningScheduleEntity::class,
      WatchedSegmentEntity::class,
      ReminderSettingEntity::class,
    ],
  version = 1,
)
@TypeConverters(GeometryTypeConverter::class)
abstract class ParkBuddyDatabase : RoomDatabase() {
  abstract fun streetCleaningDao(): StreetCleaningDao

  abstract fun reminderDao(): ReminderDao
}
