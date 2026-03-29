package dev.bongballe.parkbuddy.data.sf.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for the SF Open Data Meter Policies API (daily-updated).
 *
 * API: https://data.sfgov.org/resource/qq7v-hds4.json
 *
 * This dataset is extracted from the SFMTA Meter API and updated daily. It provides per-day
 * schedules with hourly rates for each meter, replacing the frozen-in-2014 Meter Operating
 * Schedules (6cqg-dxku) for meters it covers (~3,700 smart meters).
 *
 * Schedule types:
 * - FREE: No charge, no limit
 * - OP: Standard operating rate
 * - ALT: Alternate/demand-responsive rate
 * - PRE: Pre-paid/early bird (time limit applies but no hourly charge)
 * - TOW: Tow-away zone
 *
 * @property postId Meter ID (e.g. "102-03980"), matches with ParkingMeterResponse
 * @property dayOfWeek Day abbreviation (e.g. "Mo", "Tu", "Fr", "Su")
 * @property startTime Start time in HH:MM 24h format (e.g. "0:00", "8:00", "15:00")
 * @property endTime End time in HH:MM 24h format (e.g. "4:30", "18:00", "24:00")
 * @property scheduleType One of: "FREE", "OP", "ALT", "PRE", "TOW"
 * @property hourlyRate Hourly rate in USD. Present for OP and ALT types; absent for FREE/PRE/TOW.
 * @property timeLimitMinutes Time limit in minutes. Present for OP, ALT, and PRE types.
 * @property capColor Meter cap color (e.g. "Grey", "Yellow", "Green")
 */
@Serializable
data class MeterPolicyResponse(
  @SerialName("postid") val postId: String = "",
  @SerialName("dayofweek") val dayOfWeek: String = "",
  @SerialName("starttime") val startTime: String = "",
  @SerialName("endtime") val endTime: String = "",
  @SerialName("scheduletype") val scheduleType: String = "",
  @SerialName("hourlyrate") val hourlyRate: String? = null,
  @SerialName("timelimitminutes") val timeLimitMinutes: String? = null,
  @SerialName("capcolor") val capColor: String? = null,
)
