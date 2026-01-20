package dev.bongballe.parkbuddy.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import dev.bongballe.parkbuddy.model.StreetSide

@Entity(
  tableName = "watched_segments",
  primaryKeys = ["cnn", "side"],
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
data class WatchedSegmentEntity(
  val cnn: String,
  val side: StreetSide,
  val addedAt: Long = System.currentTimeMillis(),
)
