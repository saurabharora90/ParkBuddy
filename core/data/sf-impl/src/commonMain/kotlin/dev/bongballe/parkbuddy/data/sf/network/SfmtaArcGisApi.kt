package dev.bongballe.parkbuddy.data.sf.network

import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisBlockfaceAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisBlockfaceRateAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisCleaningAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisFeature
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisFeatureResponse
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisMeterAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisRegulationAttrs
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Client for the SFMTA ArcGIS REST parking services.
 *
 * Two base services:
 * - `parking/FeatureServer` - street cleaning, meters, blockfaces, regulations
 * - `sfpark_ODS/MapServer` - blockface rates and meter inventory detail
 *
 * Pagination uses `resultOffset` + `resultRecordCount`. When `exceededTransferLimit` is true in the
 * response, there are more records to fetch.
 */
open class SfmtaArcGisApi(private val client: HttpClient, private val baseUrl: String) {

  companion object {
    private const val BATCH_SIZE = 5000
  }

  /** Street cleaning schedules (Layer 3). 37,904 records, polyline. */
  open suspend fun getStreetCleaning(
    offset: Int = 0,
    limit: Int = BATCH_SIZE,
  ): ArcGisFeatureResponse<ArcGisCleaningAttrs> =
    queryLayer("parking/FeatureServer/3", offset, limit)

  /** Metered blockfaces with curbside polyline geometry (Layer 12). 3,141 records. */
  open suspend fun getMeteredBlockfaces(
    offset: Int = 0,
    limit: Int = BATCH_SIZE,
  ): ArcGisFeatureResponse<ArcGisBlockfaceAttrs> =
    queryLayer("parking/FeatureServer/12", offset, limit)

  /** Individual meter locations with BLOCKFACE_ID + CNN bridge (Layer 11). 38,519 records. */
  open suspend fun getMeters(
    offset: Int = 0,
    limit: Int = BATCH_SIZE,
  ): ArcGisFeatureResponse<ArcGisMeterAttrs> = queryLayer("parking/FeatureServer/11", offset, limit)

  /** Time-limited parking regulations with RPP (Layer 9). 6,891 records, polyline. */
  open suspend fun getTimeLimitedRegulations(
    offset: Int = 0,
    limit: Int = BATCH_SIZE,
  ): ArcGisFeatureResponse<ArcGisRegulationAttrs> =
    queryLayer("parking/FeatureServer/9", offset, limit)

  /** Other regulations: tow-away, no parking, no stopping, etc. (Layer 10). 871 records. */
  open suspend fun getOtherRegulations(
    offset: Int = 0,
    limit: Int = BATCH_SIZE,
  ): ArcGisFeatureResponse<ArcGisRegulationAttrs> =
    queryLayer("parking/FeatureServer/10", offset, limit)

  /** Blockface rate schedules (ODS Layer 4). 2,795 records, polyline. */
  open suspend fun getBlockfaceRates(
    offset: Int = 0,
    limit: Int = BATCH_SIZE,
  ): ArcGisFeatureResponse<ArcGisBlockfaceRateAttrs> =
    queryLayer("sfpark_ODS/MapServer/4", offset, limit)

  private suspend inline fun <reified T> queryLayer(
    layerPath: String,
    offset: Int,
    limit: Int,
  ): ArcGisFeatureResponse<T> =
    client
      .get("${baseUrl}${layerPath}/query") {
        parameter("where", "1=1")
        parameter("outFields", "*")
        parameter("f", "json")
        parameter("outSR", "4326")
        parameter("returnGeometry", "true")
        parameter("resultOffset", offset)
        parameter("resultRecordCount", limit)
      }
      .body()
}

/** Fetches all pages of an ArcGIS query into a flat list of features. */
suspend fun <T> fetchAllArcGis(
  fetch: suspend (offset: Int, limit: Int) -> ArcGisFeatureResponse<T>
): List<ArcGisFeature<T>> {
  val all = mutableListOf<ArcGisFeature<T>>()
  var offset = 0
  val limit = 5000
  while (true) {
    val response =
      try {
        fetch(offset, limit)
      } catch (_: kotlinx.io.IOException) {
        break
      }
    all.addAll(response.features)
    if (!response.exceededTransferLimit) break
    offset += limit
  }
  return all
}
