package dev.bongballe.parkbuddy.data.sf.network

import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisBlockfaceAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisBlockfaceRateAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisCleaningAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisFeature
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisFeatureResponse
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisMeterAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisRegulationAttrs
import io.ktor.client.HttpClient

class FakeSfmtaArcGisApi : SfmtaArcGisApi(HttpClient(), "") {
  var streetCleaning: List<ArcGisFeature<ArcGisCleaningAttrs>> = emptyList()
  var meteredBlockfaces: List<ArcGisFeature<ArcGisBlockfaceAttrs>> = emptyList()
  var meters: List<ArcGisFeature<ArcGisMeterAttrs>> = emptyList()
  var timeLimitedRegulations: List<ArcGisFeature<ArcGisRegulationAttrs>> = emptyList()
  var otherRegulations: List<ArcGisFeature<ArcGisRegulationAttrs>> = emptyList()
  var blockfaceRates: List<ArcGisFeature<ArcGisBlockfaceRateAttrs>> = emptyList()

  private fun <T> respond(
    list: List<ArcGisFeature<T>>,
    offset: Int,
    limit: Int,
  ): ArcGisFeatureResponse<T> {
    val start = offset.coerceAtMost(list.size)
    val end = (offset + limit).coerceAtMost(list.size)
    return ArcGisFeatureResponse(
      features = list.subList(start, end),
      exceededTransferLimit = end < list.size,
    )
  }

  override suspend fun getStreetCleaning(offset: Int, limit: Int) =
    respond(streetCleaning, offset, limit)

  override suspend fun getMeteredBlockfaces(offset: Int, limit: Int) =
    respond(meteredBlockfaces, offset, limit)

  override suspend fun getMeters(offset: Int, limit: Int) = respond(meters, offset, limit)

  override suspend fun getTimeLimitedRegulations(offset: Int, limit: Int) =
    respond(timeLimitedRegulations, offset, limit)

  override suspend fun getOtherRegulations(offset: Int, limit: Int) =
    respond(otherRegulations, offset, limit)

  override suspend fun getBlockfaceRates(offset: Int, limit: Int) =
    respond(blockfaceRates, offset, limit)
}
