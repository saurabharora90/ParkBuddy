package dev.bongballe.parkbuddy.data.sf.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for SF Open Data Meter Operating Schedules API.
 *
 * API: https://data.sfgov.org/resource/6cqg-dxku.json
 *
 * This dataset provides the specific operating hours and time limits for each meter.
 *
 * @property streetAndBlock Street name and block number (e.g. "01ST ST 0")
 * @property postId Meter ID (e.g. "201-00040"), matches with ParkingMeterResponse
 * @property scheduleType "Operating Schedule", "Tow", etc.
 * @property daysApplied Days of the week (e.g. "Mo,Tu,We,Th,Fr")
 * @property fromTime Start time (e.g. "7:00 AM")
 * @property toTime End time (e.g. "6:00 PM")
 * @property timeLimit Time limit in minutes (e.g. "60 minutes")
 * @property priority Rule priority (e.g. "1", "2"). Higher numbers override lower ones.
 * @property appliedColorRule Meter color rule (e.g. "Yellow - Commercial loading zone").
 */
@Serializable
data class MeterScheduleResponse(
  @SerialName("street_and_block") val streetAndBlock: String? = null,
  @SerialName("post_id") val postId: String? = null,
  @SerialName("schedule_type") val scheduleType: String? = null,
  @SerialName("days_applied") val daysApplied: String? = null,
  @SerialName("from_time") val fromTime: String? = null,
  @SerialName("to_time") val toTime: String? = null,
  @SerialName("time_limit") val timeLimit: String? = null,
  @SerialName("priority") val priority: String? = null,
  @SerialName("applied_color_rule") val appliedColorRule: String? = null,
)
