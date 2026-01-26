package dev.bongballe.parkbuddy.data.sf.network

import dev.bongballe.parkbuddy.data.sf.model.ParkingRegulationResponse
import dev.bongballe.parkbuddy.data.sf.model.StreetCleaningResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface SfOpenDataApi {
  @GET("resource/yhqp-riqs.json")
  suspend fun getStreetCleaningData(
    @Query("\$limit") limit: Int = 1000,
    @Query("\$offset") offset: Int = 0,
  ): Result<List<StreetCleaningResponse>>

  @GET("resource/hi6h-neyh.json")
  suspend fun getParkingRegulations(
    @Query("\$limit") limit: Int = 1000,
    @Query("\$offset") offset: Int = 0,
  ): Result<List<ParkingRegulationResponse>>
}
