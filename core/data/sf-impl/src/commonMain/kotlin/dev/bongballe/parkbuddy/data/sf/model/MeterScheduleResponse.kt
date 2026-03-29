package dev.bongballe.parkbuddy.data.sf.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for SF Open Data Meter Operating Schedules API (frozen 2014).
 *
 * API: https://data.sfgov.org/resource/6cqg-dxku.json
 *
 * Covers 29,371 meters. Used as a fallback for meters not in the daily-updated
 * [MeterPolicyResponse] dataset. The data is from March 2014; rates are stale but enforcement hours
 * and time limits are still accurate for the vast majority of meters.
 */
@Serializable
data class MeterScheduleResponse(
  @SerialName("post_id") val postId: String? = null,
  @SerialName("schedule_type") val scheduleType: String? = null,
  @SerialName("days_applied") val daysApplied: String? = null,
  @SerialName("from_time") val fromTime: String? = null,
  @SerialName("to_time") val toTime: String? = null,
  @SerialName("time_limit") val timeLimit: String? = null,
  @SerialName("applied_color_rule") val appliedColorRule: String? = null,
)
