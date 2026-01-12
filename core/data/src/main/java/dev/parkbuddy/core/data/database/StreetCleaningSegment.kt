package dev.parkbuddy.core.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "street_cleaning_segments")
data class StreetCleaningSegment(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val schedule: String,
  val locationData: String, // Storing as String for now, can be JSON or specialized type
  val isWatched: Boolean = false,
)
