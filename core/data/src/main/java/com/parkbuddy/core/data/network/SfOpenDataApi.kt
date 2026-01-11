package com.parkbuddy.core.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

interface SfOpenDataApi {
    @GET("resource/yhqp-riqs.json")
    suspend fun getStreetCleaningData(
        @Query("\$limit") limit: Int = 1000
    ): List<StreetCleaningResponse>
}

@JsonClass(generateAdapter = true)
data class StreetCleaningResponse(
    val cnn: String?,
    @Json(name = "corridor") val streetname: String?,
    @Json(name = "week_1") val week1: String?,
    @Json(name = "week_2") val week2: String?,
    @Json(name = "week_3") val week3: String?,
    @Json(name = "week_4") val week4: String?,
    @Json(name = "week_5") val week5: String?,
    val weekday: String?,
    @Json(name = "from_hour") val fromhour: String?,
    @Json(name = "to_hour") val tohour: String?,
    @Json(name = "line") val geometry: Geometry?
)

@JsonClass(generateAdapter = true)
data class Geometry(
    val type: String?,
    val coordinates: List<List<Double>>?
)