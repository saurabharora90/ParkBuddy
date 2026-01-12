package dev.parkbuddy.core.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface SfOpenDataApi {
  @GET("resource/yhqp-riqs.json")
  suspend fun getStreetCleaningData(
    @Query("\$limit") limit: Int = 1000
  ): List<StreetCleaningResponse>
}

@Serializable
data class StreetCleaningResponse(
  val cnn: String?,
  @SerialName("corridor") val streetname: String?,
  @SerialName("week_1") val week1: String?,
  @SerialName("week_2") val week2: String?,
  @SerialName("week_3") val week3: String?,
  @SerialName("week_4") val week4: String?,
  @SerialName("week_5") val week5: String?,
  val weekday: String?,
  @SerialName("from_hour") val fromhour: String?,
  @SerialName("to_hour") val tohour: String?,
  @SerialName("line") val geometry: Geometry?,
)

@Serializable data class Geometry(val type: String?, val coordinates: List<List<Double>>?)
