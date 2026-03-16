package dev.bongballe.parkbuddy.data.sf.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Response model for SF Open Data Parking Regulations API.
 *
 * API: https://data.sfgov.org/resource/hi6h-neyh.json Dataset: Parking Regulations
 *
 * This API provides parking rules for street segments in San Francisco, including:
 * - Regulation type (time-limited, permit required, no parking, etc.)
 * - Enforcement hours and days
 * - Residential Parking Permit (RPP) zones
 * - Street segment geometry
 *
 * Note: This API does NOT include street names. Street names come from the Street Sweeping API via
 * coordinate matching.
 *
 * @property objectId Unique identifier for this parking regulation segment
 * @property regulation Type of parking allowed. Values include: "Time limited", "Time Limited",
 *   "Pay or Permit", "Residential permit only", "No parking any time", "No Parking Anytime",
 *   "Government permit", etc. Case varies inconsistently in the API.
 * @property days Days when regulation applies. Format varies: "M-F" (Mon-Fri), "M-Su" (every day),
 *   "M, TH" (Mon and Thu), "Sa" (Sat only), "m-f" (lowercase variant)
 * @property hrsBegin Enforcement start time as string (e.g., "800" for 8:00 AM, "1200" for noon)
 * @property hrsEnd Enforcement end time as string (e.g., "1800" for 6:00 PM, "2400" for midnight)
 * @property rppArea1 Primary Residential Parking Permit zone letter (e.g., "N", "A", "BB")
 * @property rppArea2 Secondary Residential Parking Permit zone letter
 * @property rppArea3 Tertiary Residential Parking Permit zone letter
 * @property hrLimit Time limit as string (e.g., "2hr", "0.5"). May be a decimal.
 * @property regDetails Additional rule details (e.g., "SAT: 5PM - 11PM").
 * @property exceptions Exceptions to rules (e.g., "Except Area N holders").
 * @property neighborhood SF neighborhood name from `analysis_neighborhood` field (e.g., "Inner
 *   Richmond", "Mission Bay")
 * @property shape GeoJSON geometry (LineString or MultiLineString) for the street segment
 */
@Serializable
data class ParkingRegulationResponse(
  @SerialName("objectid") val objectId: String = "",
  val regulation: String = "",
  val days: String? = null,
  @SerialName("hrs_begin") val hrsBegin: String? = null,
  @SerialName("hrs_end") val hrsEnd: String? = null,
  @SerialName("rpparea1") val rppArea1: String? = null,
  @SerialName("rpparea2") val rppArea2: String? = null,
  @SerialName("rpparea3") val rppArea3: String? = null,
  @SerialName("hrlimit") val hrLimit: String? = null,
  @SerialName("regdetails") val regDetails: String? = null,
  @SerialName("exceptions") val exceptions: String? = null,
  @SerialName("analysis_neighborhood") val neighborhood: String? = null,
  val shape: JsonElement? = null,
)
