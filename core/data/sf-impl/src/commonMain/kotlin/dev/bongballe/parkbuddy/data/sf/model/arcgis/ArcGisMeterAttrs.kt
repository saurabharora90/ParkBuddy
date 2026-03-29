package dev.bongballe.parkbuddy.data.sf.model.arcgis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Attributes for SFMTA ArcGIS Meters layer (parking/FeatureServer/11).
 *
 * The critical bridge: each meter has both [blockfaceId] (links to metered blockface geometry) and
 * [streetSegCtrlnId] (the CNN, links to street cleaning schedules). 38,519 records, point geometry.
 */
@Serializable
data class ArcGisMeterAttrs(
  @SerialName("OBJECTID") val objectId: Long? = null,
  @SerialName("POST_ID") val postId: String? = null,
  @SerialName("BLOCKFACE_ID") val blockfaceId: Double? = null,
  @SerialName("STREET_SEG_CTRLN_ID") val streetSegCtrlnId: Double? = null,
  @SerialName("STREET_NAME") val streetName: String? = null,
  @SerialName("STREET_NUM") val streetNum: Double? = null,
  @SerialName("CAP_COLOR") val capColor: String? = null,
  @SerialName("ACTIVE_METER_FLAG") val activeMeterFlag: String? = null,
  @SerialName("METER_TYPE") val meterType: String? = null,
  @SerialName("ON_OFFSTREET_TYPE") val onOffStreetType: String? = null,
  @SerialName("PAY_OR_PERMIT") val payOrPermit: Double? = null,
  @SerialName("RPP_EXCEPTIONS") val rppExceptions: String? = null,
  @SerialName("LONGITUDE") val longitude: Double? = null,
  @SerialName("LATITUDE") val latitude: Double? = null,
) {
  /** CNN as a string key for joining to street cleaning data. Converts 1079000.0 -> "1079000". */
  val cnn: String?
    get() = streetSegCtrlnId?.toLong()?.toString()
}
