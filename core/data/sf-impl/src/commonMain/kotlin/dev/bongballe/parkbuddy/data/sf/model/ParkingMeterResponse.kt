package dev.bongballe.parkbuddy.data.sf.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for SF Open Data Parking Meter Inventory API.
 *
 * API: https://data.sfgov.org/resource/8vzz-qzz9.json
 *
 * Each record represents a single parking meter (point). We group these by [streetSegCtrlnId] (CNN)
 * to rebuild the street segment.
 *
 * @property objectId Unique identifier
 * @property postId Meter ID (e.g. "470-09440")
 * @property streetName Street name (e.g. "HOWARD ST")
 * @property streetNum Street number (e.g. "944")
 * @property streetSegCtrlnId CNN (Street Centerline ID), crucial for matching geometry
 * @property meterType SS (Single Space), MS (Multi Space)
 * @property capColor Color of the meter cap, often indicates rate/rules
 * @property activeMeterFlag "M" (Active), "U" (Inactive), etc.
 * @property neighborhood Analysis neighborhood
 */
@Serializable
data class ParkingMeterResponse(
  @SerialName("objectid") val objectId: String = "",
  @SerialName("post_id") val postId: String = "",
  @SerialName("street_name") val streetName: String? = null,
  @SerialName("street_num") val streetNum: String? = null,
  @SerialName("street_seg_ctrln_id") val streetSegCtrlnId: String? = null,
  @SerialName("meter_type") val meterType: String? = null,
  @SerialName("cap_color") val capColor: String? = null,
  @SerialName("active_meter_flag") val activeMeterFlag: String? = null,
  @SerialName("analysis_neighborhood") val neighborhood: String? = null,
)
