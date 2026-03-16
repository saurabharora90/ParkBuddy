package dev.bongballe.parkbuddy.data.sf.network

import dev.bongballe.parkbuddy.data.sf.model.MeterScheduleResponse
import dev.bongballe.parkbuddy.data.sf.model.ParkingMeterResponse
import dev.bongballe.parkbuddy.data.sf.model.ParkingRegulationResponse
import dev.bongballe.parkbuddy.data.sf.model.StreetCleaningResponse
import dev.bongballe.parkbuddy.data.sf.model.TowAwayZoneResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Client for the SF Open Data API using Ktor.
 *
 * Each method is a simple GET with pagination parameters, returning the deserialized response body
 * directly (throws on failure).
 */
open class SfOpenDataApi(private val client: HttpClient, private val baseUrl: String) {

  /** Fetches street cleaning (sweeping) schedules. */
  open suspend fun getStreetCleaningData(
    limit: Int,
    offset: Int = 0,
  ): List<StreetCleaningResponse> =
    client
      .get("${baseUrl}resource/yhqp-riqs.json") {
        parameter("\$limit", limit)
        parameter("\$offset", offset)
      }
      .body()

  /** Fetches general parking regulations (RPP, Time Limits, etc.). */
  open suspend fun getParkingRegulations(
    limit: Int,
    offset: Int = 0,
  ): List<ParkingRegulationResponse> =
    client
      .get("${baseUrl}resource/hi6h-neyh.json") {
        parameter("\$limit", limit)
        parameter("\$offset", offset)
      }
      .body()

  /** Fetches the inventory of all parking meters. */
  open suspend fun getParkingMeterInventory(
    limit: Int,
    offset: Int = 0,
  ): List<ParkingMeterResponse> =
    client
      .get("${baseUrl}resource/8vzz-qzz9.json") {
        parameter("\$limit", limit)
        parameter("\$offset", offset)
      }
      .body()

  /** Fetches specific operating schedules and time limits for meters. */
  open suspend fun getMeterSchedules(limit: Int, offset: Int = 0): List<MeterScheduleResponse> =
    client
      .get("${baseUrl}resource/6cqg-dxku.json") {
        parameter("\$limit", limit)
        parameter("\$offset", offset)
      }
      .body()

  /**
   * Fetches regularly scheduled tow-away zones.
   *
   * This is a separate dataset from meter schedules. It covers AM/PM peak tow-away on major
   * arterials that aren't captured in the meter schedule API.
   */
  open suspend fun getTowAwayZones(limit: Int, offset: Int = 0): List<TowAwayZoneResponse> =
    client
      .get("${baseUrl}resource/ynvq-waab.json") {
        parameter("\$limit", limit)
        parameter("\$offset", offset)
      }
      .body()
}
