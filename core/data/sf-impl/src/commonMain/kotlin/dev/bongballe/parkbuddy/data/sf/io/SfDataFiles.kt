package dev.bongballe.parkbuddy.data.sf.io

/** File names for the bundled/downloaded SF parking data. All files are gzipped. */
object SfDataFiles {
  const val CLEANING = "cleaning.json.gz"
  const val BLOCKFACES = "blockfaces.json.gz"
  const val METERS = "meters.json.gz"
  const val REGULATIONS_TIMED = "regulations_timed.json.gz"
  const val REGULATIONS_OTHER = "regulations_other.json.gz"
  const val BLOCKFACE_RATES = "blockface_rates.json.gz"
  const val METER_POLICIES = "meter_policies.json.gz"
  const val METER_SCHEDULES = "meter_schedules.json.gz"
  const val CENTERLINES = "centerlines.json.gz"
}
