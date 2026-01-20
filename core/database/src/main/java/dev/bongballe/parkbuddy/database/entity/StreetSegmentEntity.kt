package dev.bongballe.parkbuddy.database.entity

import androidx.room.Entity
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.StreetSide

@Entity(tableName = "street_segments", primaryKeys = ["cnn", "side"])
data class StreetSegmentEntity(
  val cnn: String,
  val streetName: String,
  val limits: String,
  val blockSide: String,
  val side: StreetSide,
  val geometry: Geometry,
)
