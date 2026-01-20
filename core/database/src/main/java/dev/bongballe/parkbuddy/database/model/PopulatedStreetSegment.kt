package dev.bongballe.parkbuddy.database.model

import androidx.room.Embedded
import androidx.room.Relation
import dev.bongballe.parkbuddy.database.entity.CleaningScheduleEntity
import dev.bongballe.parkbuddy.database.entity.StreetSegmentEntity
import dev.bongballe.parkbuddy.database.entity.WatchedSegmentEntity

data class PopulatedStreetSegment(
  @Embedded val segment: StreetSegmentEntity,
  @Relation(parentColumn = "cnn", entityColumn = "cnn", entity = CleaningScheduleEntity::class)
  val allSchedules: List<CleaningScheduleEntity>,
  @Relation(parentColumn = "cnn", entityColumn = "cnn", entity = WatchedSegmentEntity::class)
  val allWatchStatuses: List<WatchedSegmentEntity>,
) {
  // Filter schedules by matching BOTH cnn AND side
  val schedules: List<CleaningScheduleEntity>
    get() = allSchedules.filter { it.cnn == segment.cnn && it.side == segment.side }

  val watchStatus: WatchedSegmentEntity?
    get() = allWatchStatuses.find { it.cnn == segment.cnn && it.side == segment.side }
}
