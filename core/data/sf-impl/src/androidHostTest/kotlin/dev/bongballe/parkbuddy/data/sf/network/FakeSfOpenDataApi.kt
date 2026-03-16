package dev.bongballe.parkbuddy.data.sf.network

import dev.bongballe.parkbuddy.data.sf.model.MeterScheduleResponse
import dev.bongballe.parkbuddy.data.sf.model.ParkingMeterResponse
import dev.bongballe.parkbuddy.data.sf.model.ParkingRegulationResponse
import dev.bongballe.parkbuddy.data.sf.model.StreetCleaningResponse
import dev.bongballe.parkbuddy.data.sf.model.TowAwayZoneResponse
import io.ktor.client.HttpClient

class FakeSfOpenDataApi : SfOpenDataApi(HttpClient(), "") {
  var streetCleaningData: List<StreetCleaningResponse> = emptyList()
  var parkingRegulations: List<ParkingRegulationResponse> = emptyList()
  var parkingMeterInventory: List<ParkingMeterResponse> = emptyList()
  var meterSchedules: List<MeterScheduleResponse> = emptyList()

  override suspend fun getStreetCleaningData(
    limit: Int,
    offset: Int,
  ): List<StreetCleaningResponse> {
    val start = offset.coerceAtMost(streetCleaningData.size)
    val end = (offset + limit).coerceAtMost(streetCleaningData.size)
    return streetCleaningData.subList(start, end)
  }

  override suspend fun getParkingRegulations(
    limit: Int,
    offset: Int,
  ): List<ParkingRegulationResponse> {
    val start = offset.coerceAtMost(parkingRegulations.size)
    val end = (offset + limit).coerceAtMost(parkingRegulations.size)
    return parkingRegulations.subList(start, end)
  }

  override suspend fun getParkingMeterInventory(
    limit: Int,
    offset: Int,
  ): List<ParkingMeterResponse> {
    val start = offset.coerceAtMost(parkingMeterInventory.size)
    val end = (offset + limit).coerceAtMost(parkingMeterInventory.size)
    return parkingMeterInventory.subList(start, end)
  }

  override suspend fun getMeterSchedules(limit: Int, offset: Int): List<MeterScheduleResponse> {
    val start = offset.coerceAtMost(meterSchedules.size)
    val end = (offset + limit).coerceAtMost(meterSchedules.size)
    return meterSchedules.subList(start, end)
  }

  var towAwayZones: List<TowAwayZoneResponse> = emptyList()

  override suspend fun getTowAwayZones(limit: Int, offset: Int): List<TowAwayZoneResponse> {
    val start = offset.coerceAtMost(towAwayZones.size)
    val end = (offset + limit).coerceAtMost(towAwayZones.size)
    return towAwayZones.subList(start, end)
  }
}
