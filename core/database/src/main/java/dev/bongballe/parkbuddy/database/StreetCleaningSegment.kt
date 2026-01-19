package dev.bongballe.parkbuddy.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.bongballe.parkbuddy.model.Geometry

@Entity(tableName = "street_cleaning_segments")
data class StreetCleaningSegment(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val schedule: String,
  val locationData: Geometry,
  val isWatched: Boolean = false,
)
