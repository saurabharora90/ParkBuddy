package dev.bongballe.parkbuddy.data.sf.network

import dev.bongballe.parkbuddy.data.sf.model.ParkingRegulationResponse
import dev.bongballe.parkbuddy.data.sf.model.StreetCleaningResponse

class FakeSfOpenDataApi : SfOpenDataApi {
  var streetCleaningData: List<StreetCleaningResponse> = emptyList()
  var parkingRegulations: List<ParkingRegulationResponse> = emptyList()

  override suspend fun getStreetCleaningData(
    limit: Int,
    offset: Int,
  ): Result<List<StreetCleaningResponse>> {
    val start = offset.coerceAtMost(streetCleaningData.size)
    val end = (offset + limit).coerceAtMost(streetCleaningData.size)
    return Result.success(streetCleaningData.subList(start, end))
  }

  override suspend fun getParkingRegulations(
    limit: Int,
    offset: Int,
  ): Result<List<ParkingRegulationResponse>> {
    val start = offset.coerceAtMost(parkingRegulations.size)
    val end = (offset + limit).coerceAtMost(parkingRegulations.size)
    return Result.success(parkingRegulations.subList(start, end))
  }
}
