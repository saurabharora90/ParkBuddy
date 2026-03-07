package dev.bongballe.parkbuddy.data.sf.network

import dev.bongballe.parkbuddy.data.sf.model.MeterScheduleResponse
import dev.bongballe.parkbuddy.data.sf.model.ParkingMeterResponse
import dev.bongballe.parkbuddy.data.sf.model.ParkingRegulationResponse
import dev.bongballe.parkbuddy.data.sf.model.StreetCleaningResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface SfOpenDataApi {
  /**
   * Fetches street cleaning (sweeping) schedules.
   *
   * This is the source of truth for WHEN a street is cleaned. It provides the CNN (Street
   * Centerline ID) and the schedule (day, hours, week of month). It also provides the polyline
   * geometry for the street segment.
   *
   * API: https://data.sfgov.org/resource/yhqp-riqs.json
   */
  @GET("resource/yhqp-riqs.json")
  suspend fun getStreetCleaningData(
    @Query("\$limit") limit: Int = 1000,
    @Query("\$offset") offset: Int = 0,
  ): Result<List<StreetCleaningResponse>>

  /**
   * Fetches general parking regulations (RPP, Time Limits, etc.).
   *
   * This is the primary source for residential permit zones (RPP) and unmetered time-limited
   * parking. However, this dataset is often missing dedicated metered parking segments in
   * commercial areas.
   *
   * API: https://data.sfgov.org/resource/hi6h-neyh.json
   */
  @GET("resource/hi6h-neyh.json")
  suspend fun getParkingRegulations(
    @Query("\$limit") limit: Int = 1000,
    @Query("\$offset") offset: Int = 0,
  ): Result<List<ParkingRegulationResponse>>

  /**
   * Fetches the inventory of all parking meters.
   *
   * Since the "Parking Regulations" API often excludes metered streets, we use this dataset to
   * identify paid parking areas. Each record is a single meter point; we group them by CNN to
   * reconstruct the "Paid Parking" street segments.
   *
   * API: https://data.sfgov.org/resource/8vzz-qzz9.json
   */
  @GET("resource/8vzz-qzz9.json")
  suspend fun getParkingMeterInventory(
    @Query("\$limit") limit: Int = 1000,
    @Query("\$offset") offset: Int = 0,
  ): Result<List<ParkingMeterResponse>>

  /**
   * Fetches specific operating schedules and time limits for meters.
   *
   * While the inventory API tells us WHERE a meter is, this API tells us WHEN it is active and what
   * its TIME LIMIT is (e.g., 60 mins, 120 mins).
   *
   * API: https://data.sfgov.org/resource/6cqg-dxku.json
   */
  @GET("resource/6cqg-dxku.json")
  suspend fun getMeterSchedules(
    @Query("\$limit") limit: Int = 1000,
    @Query("\$offset") offset: Int = 0,
  ): Result<List<MeterScheduleResponse>>
}
