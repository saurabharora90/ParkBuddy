package dev.bongballe.parkbuddy.model

data class StreetCleaningSegmentModel(
  val cnn: String,
  val streetName: String,
  val limits: String,
  val blockSide: String,
  val side: StreetSide,
  val schedule: String,
  val locationData: Geometry,
  val isWatched: Boolean,
  val weeks: List<Boolean>, // [week1, week2, week3, week4, week5]
  val servicedOnHolidays: Boolean,
) {
  val id: String get() = "${cnn}_${side.name}"
}
