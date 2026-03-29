package dev.bongballe.parkbuddy.data.sf.network

import dev.bongballe.parkbuddy.data.sf.model.MeterPolicyResponse
import dev.bongballe.parkbuddy.data.sf.model.MeterScheduleResponse
import dev.bongballe.parkbuddy.data.sf.model.StreetCenterlineResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Client for SF Open Data (Socrata) API.
 *
 * Used for meter schedules. All other parking data comes from [SfmtaArcGisApi].
 */
open class SfOpenDataApi(private val client: HttpClient, private val baseUrl: String) {

  open suspend fun getMeterPolicies(limit: Int, offset: Int = 0): List<MeterPolicyResponse> =
    client
      .get("${baseUrl}resource/qq7v-hds4.json") {
        parameter("\$limit", limit)
        parameter("\$offset", offset)
      }
      .body()

  open suspend fun getMeterSchedules(limit: Int, offset: Int = 0): List<MeterScheduleResponse> =
    client
      .get("${baseUrl}resource/6cqg-dxku.json") {
        parameter("\$limit", limit)
        parameter("\$offset", offset)
      }
      .body()

  open suspend fun getStreetCenterlines(
    limit: Int,
    offset: Int = 0,
  ): List<StreetCenterlineResponse> =
    client
      .get("${baseUrl}resource/3psu-pn9h.json") {
        parameter("\$limit", limit)
        parameter("\$offset", offset)
        parameter("\$where", "active='true'")
        parameter("\$select", "cnn,streetname,nhood,classcode,line")
      }
      .body()
}
