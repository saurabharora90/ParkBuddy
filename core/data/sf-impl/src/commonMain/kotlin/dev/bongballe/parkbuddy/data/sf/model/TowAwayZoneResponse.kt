package dev.bongballe.parkbuddy.data.sf.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Response model for SF Open Data Regularly Scheduled Tow-Away Zones API.
 *
 * API: https://data.sfgov.org/resource/ynvq-waab.json
 *
 * This dataset provides blockface-level tow-away zone schedules. Unlike the meter schedules API
 * (which only has tow data for metered blocks), this covers ALL regularly scheduled tow zones
 * including AM/PM peak hour tow-away on major arterials.
 *
 * Each record represents one side of a street block with up to two tow windows.
 *
 * @property cnn Centerline Network Number, matches sweeping and meter data
 * @property side "Right" or "Left" (relative to centerline direction)
 * @property street Street name (e.g., "BRYANT", "02ND")
 * @property stType Street type suffix (e.g., "ST", "AVE", "BLVD")
 * @property tow1Days Days for first tow window (e.g., "Mo,Tu,We,Th,Fr")
 * @property tow1Start Start hour in 24h format (e.g., "700" for 7 AM, "1600" for 4 PM)
 * @property tow1End End hour in 24h format (e.g., "900", "1900")
 * @property tow2Days Days for optional second tow window (null or empty if none)
 * @property tow2Start Start hour for second window ("0" if none)
 * @property tow2End End hour for second window ("0" if none)
 * @property towPeriod Human label: "AMpeak", "PMpeak", "AMPMpeak"
 * @property geometry GeoJSON LineString geometry for this blockface
 */
@Serializable
data class TowAwayZoneResponse(
  @SerialName("cnn") val cnn: String = "",
  @SerialName("side") val side: String = "",
  @SerialName("street") val street: String = "",
  @SerialName("st_type") val stType: String = "",
  @SerialName("tow1days") val tow1Days: String? = null,
  @SerialName("tow1start") val tow1Start: String? = null,
  @SerialName("tow1end") val tow1End: String? = null,
  @SerialName("tow2days") val tow2Days: String? = null,
  @SerialName("tow2start") val tow2Start: String? = null,
  @SerialName("tow2end") val tow2End: String? = null,
  @SerialName("towperiod") val towPeriod: String? = null,
  @SerialName("geometry") val geometry: JsonElement? = null,
)
