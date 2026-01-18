package dev.bongballe.parkbuddy.data.network

import dev.bongballe.parkbuddy.data.model.StreetCleaningResponse
import retrofit2.http.GET

interface SfOpenDataApi {
  @GET("resource/yhqp-riqs.json") suspend fun getStreetCleaningData(): List<StreetCleaningResponse>
}
