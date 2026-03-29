package dev.bongballe.parkbuddy.data.sf.model.arcgis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Attributes for SFMTA ArcGIS Metered Blockfaces layer (parking/FeatureServer/12).
 *
 * Each record is one side of a metered block with proper curbside polyline geometry. 3,141 records.
 */
@Serializable
data class ArcGisBlockfaceAttrs(
  @SerialName("OBJECTID") val objectId: Long? = null,
  @SerialName("BLOCKFACE_ID") val blockfaceId: Double? = null,
  @SerialName("STREET_NAME") val streetName: String? = null,
  @SerialName("FM_ADDR_NO") val fromAddrNo: Double? = null,
  @SerialName("TO_ADDR_NO") val toAddrNo: Double? = null,
  @SerialName("STR_SEG_ORIENTATION") val strSegOrientation: String? = null,
  @SerialName("BLOCKFACE_ORIENTATION") val blockfaceOrientation: String? = null,
  @SerialName("STR_NUM_PARITY") val strNumParity: String? = null,
  @SerialName("BLOCK_NUM") val blockNum: Double? = null,
  @SerialName("BLOCK_ID") val blockId: Double? = null,
  @SerialName("STREET_ID") val streetId: Double? = null,
  @SerialName("NEIGHBORHOOD_ID") val neighborhoodId: Double? = null,
  @SerialName("PM_DISTRICT_ID") val pmDistrictId: Double? = null,
  @SerialName("AREA_TYPE") val areaType: String? = null,
)
