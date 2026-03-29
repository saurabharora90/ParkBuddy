package dev.bongballe.parkbuddy.data.sf.io

/** File names for downloaded SF parking data. Written to disk during background refresh. */
object SfDataFiles {
  const val CLEANING = "cleaning.json"
  const val BLOCKFACES = "blockfaces.json"
  const val METERS = "meters.json"
  const val REGULATIONS_TIMED = "regulations_timed.json"
  const val REGULATIONS_OTHER = "regulations_other.json"
  const val BLOCKFACE_RATES = "blockface_rates.json"
  const val METER_POLICIES = "meter_policies.json"
  const val METER_SCHEDULES = "meter_schedules.json"
  const val CENTERLINES = "centerlines.json"
}
