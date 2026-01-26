package dev.bongballe.parkbuddy.data.sf.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.ParkingRegulation
import dev.bongballe.parkbuddy.model.StreetSide

/**
 * Database entity for a parkable street segment.
 *
 * Combines parking regulation data with street sweeping schedules matched via coordinate proximity.
 * Related sweeping schedules are stored in [SweepingScheduleEntity] with foreign key to this
 * entity.
 *
 * @see SweepingScheduleEntity
 * @see dev.bongballe.parkbuddy.model.ParkingSpot for domain model documentation
 */
@Entity(tableName = "parking_spots")
data class ParkingSpotEntity(
  /** Unique identifier for this parking spot */
  @PrimaryKey val objectId: String,

  /** Polyline coordinates for this street segment (stored as JSON via TypeConverter) */
  val geometry: Geometry,

  /** Street name (e.g., "Main St") */
  val streetName: String?,

  /** Cross streets defining the block (e.g., "1st Ave - 2nd Ave") */
  val blockLimits: String?,

  /** Neighborhood or district name */
  val neighborhood: String?,

  /** Type of parking regulation (stored as enum name via TypeConverter) */
  val regulation: ParkingRegulation,

  /** Residential Parking Permit zone identifier, null if not in permit zone */
  val rppArea: String?,

  /** Max parking hours, null if unlimited */
  val timeLimitHours: Int?,

  /** Days when enforcement applies (format varies by data source) */
  val enforcementDays: String?,

  /**
   * Enforcement start time as HHMM integer (e.g., 800 for 8:00 AM). Convert to LocalTime: hour =
   * value / 100, minute = value % 100
   */
  val enforcementStart: Int?,

  /**
   * Enforcement end time as HHMM integer (e.g., 1800 for 6:00 PM). Some data sources may use 2400
   * for midnight (end of day).
   */
  val enforcementEnd: Int?,

  /** Street segment identifier used for matching sweeping schedules */
  val sweepingCnn: String?,

  /** Which side of street this spot is on */
  val sweepingSide: StreetSide?,
)
