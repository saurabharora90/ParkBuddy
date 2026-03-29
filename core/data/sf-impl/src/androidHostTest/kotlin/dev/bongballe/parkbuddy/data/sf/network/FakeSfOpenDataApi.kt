package dev.bongballe.parkbuddy.data.sf.network

import dev.bongballe.parkbuddy.data.sf.model.MeterPolicyResponse
import dev.bongballe.parkbuddy.data.sf.model.MeterScheduleResponse
import dev.bongballe.parkbuddy.data.sf.model.StreetCenterlineResponse
import io.ktor.client.HttpClient

class FakeSfOpenDataApi : SfOpenDataApi(HttpClient(), "") {
  var meterPolicies: List<MeterPolicyResponse> = emptyList()
  var meterSchedules: List<MeterScheduleResponse> = emptyList()
  var streetCenterlines: List<StreetCenterlineResponse> = emptyList()

  private fun <T> paginate(list: List<T>, limit: Int, offset: Int): List<T> {
    val start = offset.coerceAtMost(list.size)
    val end = (offset + limit).coerceAtMost(list.size)
    return list.subList(start, end)
  }

  override suspend fun getMeterPolicies(limit: Int, offset: Int) =
    paginate(meterPolicies, limit, offset)

  override suspend fun getMeterSchedules(limit: Int, offset: Int) =
    paginate(meterSchedules, limit, offset)

  override suspend fun getStreetCenterlines(limit: Int, offset: Int) =
    paginate(streetCenterlines, limit, offset)
}
