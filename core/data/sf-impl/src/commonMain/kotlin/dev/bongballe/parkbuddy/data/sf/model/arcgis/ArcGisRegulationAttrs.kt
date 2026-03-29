package dev.bongballe.parkbuddy.data.sf.model.arcgis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Attributes for SFMTA ArcGIS regulation layers.
 *
 * Shared by both Layer 9 (Time Limited, 6,891 records) and Layer 10 (Other Regulations, 871
 * records). The schemas are identical; only the REGULATION values differ.
 *
 * Layer 9 values: "Time limited", "Time Limited", etc. Layer 10 values: "No Stopping", "No parking
 * any time", "No oversized vehicles", "No overnight parking", "Government permit", "Pay or Permit",
 * "Paid + Permit", etc.
 *
 * Polyline geometry in WGS84.
 */
@Serializable
data class ArcGisRegulationAttrs(
  @SerialName("OBJECTID") val objectId: Long? = null,
  @SerialName("REGULATION") val regulation: String? = null,
  @SerialName("DAYS") val days: String? = null,
  @SerialName("HOURS") val hours: String? = null,
  @SerialName("HRS_BEGIN") val hrsBegin: Double? = null,
  @SerialName("HRS_END") val hrsEnd: Double? = null,
  @SerialName("HRLIMIT") val hrLimit: Double? = null,
  @SerialName("RPPAREA1") val rppArea1: String? = null,
  @SerialName("RPPAREA2") val rppArea2: String? = null,
  @SerialName("RPPAREA3") val rppArea3: String? = null,
  @SerialName("EXCEPTIONS") val exceptions: String? = null,
  @SerialName("REGDETAILS") val regDetails: String? = null,
  @SerialName("FROM_TIME") val fromTime: String? = null,
  @SerialName("TO_TIME") val toTime: String? = null,
  @SerialName("LENGTH_FT") val lengthFt: Double? = null,
)
