package dev.bongballe.parkbuddy.data.sf.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * Database entity for a meter operating schedule.
 *
 * Meters can have multiple windows (e.g. Tow zone 7am-9am, Metered 9am-6pm).
 *
 * @see ParkingSpotEntity
 */
@Entity(
  tableName = "meter_schedules",
  foreignKeys =
    [
      ForeignKey(
        entity = ParkingSpotEntity::class,
        parentColumns = ["objectId"],
        childColumns = ["parkingSpotId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index("parkingSpotId")],
)
data class MeterScheduleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  /** Foreign key to [ParkingSpotEntity.objectId] */
  val parkingSpotId: String,
  val days: Set<DayOfWeek>,
  val startTime: LocalTime,
  val endTime: LocalTime,
  val timeLimitMinutes: Int,
  val isTowZone: Boolean,
)
