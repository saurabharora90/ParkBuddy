package dev.bongballe.parkbuddy.data.network

import dev.bongballe.parkbuddy.data.model.StreetCleaningResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface SfOpenDataApi {
  @GET("resource/yhqp-riqs.json")
  suspend fun getStreetCleaningData(
    @Query("\$limit") limit: Int = 1000,
    @Query("\$offset") offset: Int = 0,
  ): Result<List<StreetCleaningResponse>>
}
