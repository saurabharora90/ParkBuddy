package dev.bongballe.parkbuddy.data.sf.model.arcgis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Attributes for SFMTA ArcGIS Blockface Rates layer (sfpark_ODS/MapServer/4).
 *
 * 2,795 records with current meter rate schedules per blockface. The [rateSched] field contains an
 * HTML table with time-of-day rate and time-limit breakdowns. Used as a fallback for meters not
 * covered by the Socrata daily-updated meter policies.
 */
@Serializable
data class ArcGisBlockfaceRateAttrs(
  @SerialName("BLOCKFACE_ID") val blockfaceId: Long? = null,
  @SerialName("STREET_NAME") val streetName: String? = null,
  @SerialName("ADDR_RANGE") val addrRange: String? = null,
  @SerialName("RATE") val rate: String? = null,
  @SerialName("RATE_SCHED") val rateSched: String? = null,
  @SerialName("LATITUDE") val latitude: Double? = null,
  @SerialName("LONGITUDE") val longitude: Double? = null,
)
