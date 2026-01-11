package com.parkbuddy.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [StreetCleaningSegment::class], version = 1)
abstract class ParkBuddyDatabase : RoomDatabase() {
    abstract fun streetCleaningDao(): StreetCleaningDao
}