package dev.parkbuddy.core.domain.model

data class StreetCleaningSegmentModel(
  val id: Long,
  val schedule: String,
  val locationData: String,
  val isWatched: Boolean,
)
