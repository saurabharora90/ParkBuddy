package dev.bongballe.parkbuddy.data.sf.repository

import android.util.Log
import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.analytics.AnalyticsTracker
import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.data.sf.CoordinateMatcher
import dev.bongballe.parkbuddy.data.sf.database.ParkingDao
import dev.bongballe.parkbuddy.data.sf.database.entity.ParkingSpotEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.SweepingScheduleEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.UserPreferencesEntity
import dev.bongballe.parkbuddy.data.sf.database.model.PopulatedParkingSpot
import dev.bongballe.parkbuddy.data.sf.model.ParkingRegulationResponse
import dev.bongballe.parkbuddy.data.sf.model.StreetCleaningResponse
import dev.bongballe.parkbuddy.data.sf.model.toParkingRegulation
import dev.bongballe.parkbuddy.data.sf.model.toStreetSide
import dev.bongballe.parkbuddy.data.sf.network.SfOpenDataApi
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ReminderMinutes
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ParkingRepositoryImpl(
  private val dao: ParkingDao,
  private val api: SfOpenDataApi,
  private val analyticsTracker: AnalyticsTracker,
  @WithDispatcherType(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : ParkingRepository {

  companion object {
    private const val TAG = "ParkingRepository"
  }

  override fun getAllSpots(): Flow<List<ParkingSpot>> {
    return dao.getAllSpots().map { entities ->
      Log.d(TAG, "getAllSpots: mapping ${entities.size} entities")
      entities.map { it.toDomainModel() }
    }
  }

  override fun getSpotsByZone(zone: String): Flow<List<ParkingSpot>> {
    return dao.getSpotsByZone(zone).map { entities ->
      Log.d(TAG, "getSpotsByZone($zone): mapping ${entities.size} entities")
      entities.map { it.toDomainModel() }
    }
  }

  override fun countSpotsByZone(zone: String): Flow<Int> {
    return dao.countSpotsByZone(zone)
  }

  override fun getAllRppZones(): Flow<List<String>> {
    return dao.getAllRppZones()
  }

  override fun getUserRppZone(): Flow<String?> {
    return dao.getUserPreferences().map { it?.rppZone }
  }

  override fun getReminders(): Flow<List<ReminderMinutes>> {
    return dao.getUserPreferences().map { prefs ->
      prefs
        ?.reminderMinutes
        ?.split(",")
        ?.filter { it.isNotBlank() }
        ?.mapNotNull { it.toIntOrNull() }
        ?.map { ReminderMinutes(it) } ?: emptyList()
    }
  }

  override suspend fun setUserRppZone(zone: String?) {
    val existing = getExistingPreferences()
    dao.upsertUserPreferences(existing.copy(rppZone = zone))
    analyticsTracker.setCustomKey("rpp_zone", zone ?: "none")
  }

  override suspend fun addReminder(minutesBefore: ReminderMinutes) {
    val existing = getExistingPreferences()
    val currentReminders =
      existing.reminderMinutes
        .split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { it.toIntOrNull() }
        .toMutableSet()
    currentReminders.add(minutesBefore.value)
    dao.upsertUserPreferences(
      existing.copy(reminderMinutes = currentReminders.sorted().joinToString(","))
    )
  }

  override suspend fun removeReminder(minutesBefore: ReminderMinutes) {
    val existing = getExistingPreferences()
    val currentReminders =
      existing.reminderMinutes
        .split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { it.toIntOrNull() }
        .filter { it != minutesBefore.value }
    dao.upsertUserPreferences(
      existing.copy(reminderMinutes = currentReminders.sorted().joinToString(","))
    )
  }

  private suspend fun getExistingPreferences(): UserPreferencesEntity {
    return dao.getUserPreferences().first()
      ?: UserPreferencesEntity(id = 1, rppZone = null, reminderMinutes = "")
  }

  override suspend fun refreshData(): Boolean {
    Log.d(TAG, "refreshData: starting")
    val parkingRegulations = fetchAllParkingRegulations()
    Log.d(TAG, "refreshData: fetched ${parkingRegulations.size} parking regulations")
    if (parkingRegulations.isEmpty()) {
      Log.w(TAG, "refreshData: no parking regulations found, aborting")
      return false
    }

    val sweepingData = fetchAllSweepingData()
    Log.d(TAG, "refreshData: fetched ${sweepingData.size} sweeping records")

    // Heavy processing on background thread
    val (spots, schedules) =
      withContext(ioDispatcher) {
        Log.d(TAG, "refreshData: building spatial index...")
        val matcher = CoordinateMatcher(sweepingData)
        Log.d(TAG, "refreshData: spatial index built, starting matching...")

        val parkableRegulations = parkingRegulations.filter { it.isParkable() }
        Log.d(TAG, "refreshData: ${parkableRegulations.size} parkable regulations after filtering")

        val spotsList = mutableListOf<ParkingSpotEntity>()
        val schedulesList = mutableListOf<SweepingScheduleEntity>()

        for ((index, regulation) in parkableRegulations.withIndex()) {
          if (index % 1000 == 0) {
            Log.d(TAG, "refreshData: processing regulation $index/${parkableRegulations.size}")
          }

          val geometry = parseGeometry(regulation.shape) ?: continue
          val regulationType = regulation.regulation.toParkingRegulation()

          val sweepingMatch = matcher.findMatch(geometry)

          // Get street info from first schedule if available
          val firstSchedule = sweepingMatch?.schedules?.firstOrNull()

          val spot =
            ParkingSpotEntity(
              objectId = regulation.objectId,
              geometry = geometry,
              streetName = firstSchedule?.streetName?.takeIf { it.isNotBlank() },
              blockLimits = firstSchedule?.limits?.takeIf { it.isNotBlank() },
              neighborhood = regulation.neighborhood,
              regulation = regulationType,
              rppArea = regulation.rppArea1?.takeIf { it.isNotBlank() },
              timeLimitHours = parseTimeLimit(regulation.hrLimit),
              enforcementDays = regulation.days,
              enforcementStart = parseTime(regulation.hrsBegin),
              enforcementEnd = parseTime(regulation.hrsEnd),
              sweepingCnn = sweepingMatch?.cnn,
              sweepingSide = sweepingMatch?.side?.toStreetSide(),
            )

          spotsList.add(spot)

          // Add ALL schedules for this side of the street
          if (sweepingMatch != null) {
            for (schedule in sweepingMatch.schedules) {
              val fromHour = (schedule.fromhour.toIntOrNull() ?: 0) % 24
              val toHour = (schedule.tohour.toIntOrNull() ?: 0) % 24

              schedulesList.add(
                SweepingScheduleEntity(
                  parkingSpotId = regulation.objectId,
                  weekday = schedule.weekday,
                  fromHour = fromHour,
                  toHour = toHour,
                  week1 = schedule.servicedOnFirstWeekOfMonth,
                  week2 = schedule.servicedOnSecondWeekOfMonth,
                  week3 = schedule.servicedOnThirdWeekOfMonth,
                  week4 = schedule.servicedOnFourthWeekOfMonth,
                  week5 = schedule.servicedOnFifthWeekOfMonth,
                  holidays = schedule.servicedOnHolidays,
                )
              )
            }
          }
        }

        spotsList to schedulesList
      }

    Log.d(TAG, "refreshData: created ${spots.size} spots and ${schedules.size} schedules")
    if (spots.isNotEmpty()) {
      dao.clearAllSchedules()
      dao.clearAllSpots()
      dao.insertSpots(spots)
      dao.insertSchedules(schedules)
      Log.d(TAG, "refreshData: saved to database")
    }

    return true
  }

  private suspend fun fetchAllParkingRegulations(): List<ParkingRegulationResponse> {
    val allRegulations = mutableListOf<ParkingRegulationResponse>()
    var offset = 0
    val limit = 1000

    while (true) {
      val result = api.getParkingRegulations(limit = limit, offset = offset)
      if (result.isFailure) {
        val exception = result.exceptionOrNull()
        Log.e(TAG, "fetchAllParkingRegulations: API call failed at offset $offset", exception)
        exception?.let {
          analyticsTracker.logNonFatal(
            it,
            "API failure: fetchAllParkingRegulations at offset $offset",
          )
        }
        break
      }

      val batch = result.getOrNull() ?: emptyList()
      Log.d(TAG, "fetchAllParkingRegulations: got ${batch.size} at offset $offset")
      if (batch.isEmpty()) break

      allRegulations.addAll(batch)
      offset += limit
    }

    return allRegulations
  }

  private suspend fun fetchAllSweepingData(): List<StreetCleaningResponse> {
    val allSweeping = mutableListOf<StreetCleaningResponse>()
    var offset = 0
    val limit = 1000

    while (true) {
      val result = api.getStreetCleaningData(limit = limit, offset = offset)
      if (result.isFailure) {
        val exception = result.exceptionOrNull()
        exception?.let {
          analyticsTracker.logNonFatal(it, "API failure: fetchAllSweepingData at offset $offset")
        }
        break
      }

      val batch = result.getOrNull() ?: emptyList()
      if (batch.isEmpty()) break

      allSweeping.addAll(batch.filter { it.cnn.isNotEmpty() && it.geometry != null })
      offset += limit
    }

    return allSweeping
  }

  private fun ParkingRegulationResponse.isParkable(): Boolean {
    val regulation = this.regulation.toParkingRegulation()
    return regulation.isParkable
  }

  private fun parseGeometry(shape: kotlinx.serialization.json.JsonElement?): Geometry? {
    if (shape == null) return null
    return try {
      val obj = shape.jsonObject
      val type = obj["type"]?.jsonPrimitive?.content ?: return null
      val coordsArray = obj["coordinates"]?.jsonArray ?: return null

      val coordinates =
        when (type) {
          "MultiLineString" -> {
            coordsArray.flatMap { lineArray ->
              lineArray.jsonArray.map { point ->
                point.jsonArray.map { it.jsonPrimitive.content.toDouble() }
              }
            }
          }

          "LineString" -> {
            coordsArray.map { point -> point.jsonArray.map { it.jsonPrimitive.content.toDouble() } }
          }

          else -> return null
        }

      Geometry(type = "LineString", coordinates = coordinates)
    } catch (e: Exception) {
      null
    }
  }

  private fun parseTimeLimit(hrLimit: String?): Int? {
    if (hrLimit == null) return null
    return hrLimit.filter { it.isDigit() }.toIntOrNull()
  }

  private fun parseTime(timeStr: String?): Int? {
    if (timeStr == null) return null
    return timeStr.filter { it.isDigit() }.toIntOrNull()
  }

  private fun timeToLocalTime(time: Int): LocalTime {
    val hour = time / 100
    val minute = time % 100
    // API returns 2400 for midnight (end of day), normalize to 23:59
    return if (hour >= 24) {
      LocalTime(23, 59)
    } else {
      LocalTime(hour, minute.coerceIn(0, 59))
    }
  }

  private fun PopulatedParkingSpot.toDomainModel(): ParkingSpot {
    return ParkingSpot(
      objectId = spot.objectId,
      geometry = spot.geometry,
      streetName = spot.streetName,
      blockLimits = spot.blockLimits,
      neighborhood = spot.neighborhood,
      regulation = spot.regulation,
      rppArea = spot.rppArea,
      timeLimitHours = spot.timeLimitHours,
      enforcementDays = spot.enforcementDays,
      enforcementStart = spot.enforcementStart?.let { timeToLocalTime(it) },
      enforcementEnd = spot.enforcementEnd?.let { timeToLocalTime(it) },
      sweepingCnn = spot.sweepingCnn,
      sweepingSide = spot.sweepingSide,
      sweepingSchedules =
        schedules.map { schedule ->
          SweepingSchedule(
            weekday = schedule.weekday,
            fromHour = schedule.fromHour,
            toHour = schedule.toHour,
            week1 = schedule.week1,
            week2 = schedule.week2,
            week3 = schedule.week3,
            week4 = schedule.week4,
            week5 = schedule.week5,
            holidays = schedule.holidays,
          )
        },
    )
  }
}
