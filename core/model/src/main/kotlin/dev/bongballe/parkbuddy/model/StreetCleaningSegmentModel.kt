package dev.bongballe.parkbuddy.model

data class StreetCleaningSegmentModel(
  val id: Long,
  val schedule: String,
  val locationData: Geometry,
  val isWatched: Boolean,
)
