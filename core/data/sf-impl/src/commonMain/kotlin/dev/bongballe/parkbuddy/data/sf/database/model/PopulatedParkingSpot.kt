package dev.bongballe.parkbuddy.data.sf.database.model

import androidx.room.Embedded
import androidx.room.Relation
import dev.bongballe.parkbuddy.data.sf.database.entity.ParkingSpotEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.SweepingScheduleEntity

data class PopulatedParkingSpot(
  @Embedded val spot: ParkingSpotEntity,
  @Relation(parentColumn = "objectId", entityColumn = "parkingSpotId")
  val schedules: List<SweepingScheduleEntity>,
)
