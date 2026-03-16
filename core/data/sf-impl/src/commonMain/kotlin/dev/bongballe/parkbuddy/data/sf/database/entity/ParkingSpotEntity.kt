package dev.bongballe.parkbuddy.data.sf.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.model.StreetSide

/**
 * Database entity for a parkable street segment.
 *
 * Combines parking regulation data with street sweeping schedules matched via coordinate proximity.
 * Related sweeping schedules are stored in [SweepingScheduleEntity] with foreign key to this
 * entity.
 *
 * The [timeline] is a pre-resolved list of [ParkingInterval]s computed during data sync. It
 * flattens overlapping regulations and meter schedules into non-overlapping windows using
 * priority-based resolution (FORBIDDEN > RESTRICTED > METERED > LIMITED > OPEN).
 *
 * @property timeline Pre-resolved weekly parking rules. Gaps are implicitly OPEN.
 * @see SweepingScheduleEntity
 * @see dev.bongballe.parkbuddy.model.ParkingSpot for domain model documentation
 */
@Entity(tableName = "parking_spots")
data class ParkingSpotEntity(
  @PrimaryKey val objectId: String,
  val geometry: Geometry,
  val streetName: String?,
  val blockLimits: String?,
  val neighborhood: String?,
  val rppAreas: List<String>,
  /** Pre-resolved parking rules (stored as JSON via TypeConverter). */
  val timeline: List<ParkingInterval>,
  val sweepingCnn: String?,
  val sweepingSide: StreetSide?,
)
