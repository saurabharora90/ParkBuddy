package dev.bongballe.parkbuddy.model

data class StreetCleaningSegmentModel(
  val id: Long,
  val schedule: String,
  val locationData: String,
  val isWatched: Boolean,
)
