package dev.bongballe.parkbuddy.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [StreetCleaningSegment::class], version = 1)
@TypeConverters(GeometryTypeConverter::class)
abstract class ParkBuddyDatabase : RoomDatabase() {
  abstract fun streetCleaningDao(): StreetCleaningDao
}
