package dev.bongballe.parkbuddy.data.sf.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import dev.bongballe.parkbuddy.model.Weekday

/**
 * Database entity for a street sweeping schedule.
 *
 * A parking spot may have multiple schedules: one per (weekday, fromHour, toHour) combination. For
 * example, a street swept on 1st and 3rd Tuesdays 8-10 AM would have one entity with weekday=Tues,
 * fromHour=8, toHour=10, week1=true, week3=true.
 *
 * Sweeping schedules are kept separate from the timeline because they carry week-of-month semantics
 * (week1-week5) that a weekly timeline can't represent. The evaluator checks sweeping windows at
 * runtime with date-aware logic.
 *
 * Composite primary key: (parkingSpotId, weekday, fromHour, toHour) to support streets with
 * multiple sweeping windows on the same day (e.g., different hours for different week patterns).
 *
 * @see ParkingSpotEntity
 * @see dev.bongballe.parkbuddy.model.SweepingSchedule for domain model
 */
@Entity(
  tableName = "sweeping_schedules",
  primaryKeys = ["parkingSpotId", "weekday", "fromHour", "toHour"],
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
  /** Day of week when sweeping occurs. Includes [Weekday.Holiday] for holiday schedules. */
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
