package dev.bongballe.parkbuddy.data.sf.model.arcgis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Attributes for SFMTA ArcGIS Street Cleaning layer (parking/FeatureServer/3).
 *
 * Each record is one sweeping occurrence: one side of a block, one weekday. A block with sweeping
 * on both sides on two weekdays produces 4 records.
 *
 * 37,904 records total, polyline geometry (WGS84).
 */
@Serializable
data class ArcGisCleaningAttrs(
  @SerialName("CNN") val cnn: String? = null,
  @SerialName("CNNRIGHTLE") val cnnRightLeft: String? = null,
  @SerialName("CORRIDOR") val corridor: String? = null,
  @SerialName("STREETNAME") val streetName: String? = null,
  @SerialName("WEEKDAY") val weekday: String? = null,
  @SerialName("FROMHOUR") val fromHour: String? = null,
  @SerialName("TOHOUR") val toHour: String? = null,
  @SerialName("WEEK1OFMON") val week1: String? = null,
  @SerialName("WEEK2OFMON") val week2: String? = null,
  @SerialName("WEEK3OFMON") val week3: String? = null,
  @SerialName("WEEK4OFMON") val week4: String? = null,
  @SerialName("WEEK5OFMON") val week5: String? = null,
  @SerialName("HOLIDAYS") val holidays: String? = null,
  @SerialName("BLOCKSIDE") val blockSide: String? = null,
  @SerialName("LF_FADD") val leftFromAddr: String? = null,
  @SerialName("LF_TOADD") val leftToAddr: String? = null,
  @SerialName("RT_FADD") val rightFromAddr: String? = null,
  @SerialName("RT_TOADD") val rightToAddr: String? = null,
)
