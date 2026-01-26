package dev.bongballe.parkbuddy.data.sf.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import dev.bongballe.parkbuddy.model.Weekday

/**
 * Database entity for a street sweeping schedule.
 *
 * A parking spot may have multiple schedules (one per weekday that sweeping occurs). For example, a
 * street swept on Tuesdays and Fridays would have two schedule entities.
 *
 * The week1-week5 booleans indicate which weeks of the month sweeping occurs. Example: "1st and 3rd
 * Tuesday" would be: weekday=Tues, week1=true, week3=true, others=false
 *
 * Composite primary key: (parkingSpotId, weekday) since each spot has at most one schedule per
 * weekday.
 *
 * @see ParkingSpotEntity
 * @see dev.bongballe.parkbuddy.model.SweepingSchedule for domain model
 */
@Entity(
  tableName = "sweeping_schedules",
  primaryKeys = ["parkingSpotId", "weekday"],
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
data class SweepingScheduleEntity(
  /** Foreign key to [ParkingSpotEntity.objectId] */
  val parkingSpotId: String,

  /** Day of week when sweeping occurs */
  val weekday: Weekday,

  /** Start hour in 24h format (e.g., 8 for 8:00 AM) */
  val fromHour: Int,

  /** End hour in 24h format (e.g., 10 for 10:00 AM) */
  val toHour: Int,

  /** Sweeping occurs on 1st [weekday] of month */
  val week1: Boolean,

  /** Sweeping occurs on 2nd [weekday] of month */
  val week2: Boolean,

  /** Sweeping occurs on 3rd [weekday] of month */
  val week3: Boolean,

  /** Sweeping occurs on 4th [weekday] of month */
  val week4: Boolean,

  /** Sweeping occurs on 5th [weekday] of month (rare, only in months with 5 of that weekday) */
  val week5: Boolean,

  /** Sweeping occurs on holidays (typically false) */
  val holidays: Boolean,
)
