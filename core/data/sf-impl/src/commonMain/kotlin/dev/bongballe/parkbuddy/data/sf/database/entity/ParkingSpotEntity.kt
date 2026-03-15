package dev.bongballe.parkbuddy.data.sf.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.ParkingRegulation
import dev.bongballe.parkbuddy.model.StreetSide
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

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

  /** Residential Parking Permit zone identifiers (e.g., ["A", "B"]) */
  val rppAreas: List<String>,

  /** Max parking hours, null if unlimited */
  val timeLimitHours: Int?,

  /** Days when enforcement applies */
  val enforcementDays: Set<DayOfWeek>?,

  /** Enforcement start time */
  val enforcementStart: LocalTime?,

  /** Enforcement end time */
  val enforcementEnd: LocalTime?,

  /** Street segment identifier used for matching sweeping schedules */
  val sweepingCnn: String?,

  /** Which side of street this spot is on */
  val sweepingSide: StreetSide?,
)
