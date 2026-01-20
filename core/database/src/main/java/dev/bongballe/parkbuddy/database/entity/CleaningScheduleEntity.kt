package dev.bongballe.parkbuddy.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.Weekday

@Entity(
  tableName = "cleaning_schedules",
  foreignKeys =
    [
      ForeignKey(
        entity = StreetSegmentEntity::class,
        parentColumns = ["cnn", "side"],
        childColumns = ["cnn", "side"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index("cnn", "side")],
)
data class CleaningScheduleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val cnn: String,
  val side: StreetSide,
  val weekday: Weekday,
  val fromHour: String,
  val toHour: String,
  val week1: Boolean,
  val week2: Boolean,
  val week3: Boolean,
  val week4: Boolean,
  val week5: Boolean,
  val isHoliday: Boolean,
)
