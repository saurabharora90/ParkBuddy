package dev.bongballe.parkbuddy.data.sf.repository

import android.util.Log
import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.analytics.AnalyticsTracker
import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.data.sf.CoordinateMatcher
import dev.bongballe.parkbuddy.data.sf.database.ParkingDao
import dev.bongballe.parkbuddy.data.sf.database.entity.MeterScheduleEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.ParkingSpotEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.SweepingScheduleEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.UserPreferencesEntity
import dev.bongballe.parkbuddy.data.sf.database.model.PopulatedParkingSpot
import dev.bongballe.parkbuddy.data.sf.model.MeterScheduleResponse
import dev.bongballe.parkbuddy.data.sf.model.ParkingMeterResponse
import dev.bongballe.parkbuddy.data.sf.model.ParkingRegulationResponse
import dev.bongballe.parkbuddy.data.sf.model.StreetCleaningResponse
import dev.bongballe.parkbuddy.data.sf.model.toParkingRegulation
import dev.bongballe.parkbuddy.data.sf.model.toStreetSide
import dev.bongballe.parkbuddy.data.sf.network.SfOpenDataApi
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.MeterSchedule
import dev.bongballe.parkbuddy.model.ParkingRegulation
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.bongballe.parkbuddy.model.TimedRestriction
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonElement
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
  @WithDispatcherType(DispatcherType.DEFAULT) private val defaultDispatcher: CoroutineDispatcher,
) : ParkingRepository {

  private fun parseEnforcementDays(daysStr: String?): Set<DayOfWeek> {
    if (daysStr == null) return DayOfWeek.values().toSet()

    val days = mutableSetOf<DayOfWeek>()
    val normalized = daysStr.uppercase()

    if (
      normalized.contains("M-F") ||
        normalized.contains("MON-FRI") ||
        normalized.contains("WEEKDAYS")
    ) {
      days.addAll(
        listOf(
          DayOfWeek.MONDAY,
          DayOfWeek.TUESDAY,
          DayOfWeek.WEDNESDAY,
          DayOfWeek.THURSDAY,
          DayOfWeek.FRIDAY,
        )
      )
    }
    if (normalized.contains("SAT")) days.add(DayOfWeek.SATURDAY)
    if (normalized.contains("SUN")) days.add(DayOfWeek.SUNDAY)
    if (normalized.contains("MON") && !normalized.contains("MON-FRI")) days.add(DayOfWeek.MONDAY)
    if (normalized.contains("TUE")) days.add(DayOfWeek.TUESDAY)
    if (normalized.contains("WED")) days.add(DayOfWeek.WEDNESDAY)
    if (normalized.contains("THU")) days.add(DayOfWeek.THURSDAY)
    if (normalized.contains("FRI") && !normalized.contains("MON-FRI")) days.add(DayOfWeek.FRIDAY)
    if (normalized.contains("DAILY") || normalized.isBlank()) {
      days.addAll(DayOfWeek.values())
    }

    return if (days.isEmpty()) DayOfWeek.values().toSet() else days
  }

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

  override fun getAllPermitZones(): Flow<List<String>> {
    return dao.getAllRppZones()
  }

  override fun getUserPermitZone(): Flow<String?> {
    return dao.getUserPreferences().map { it?.rppZone }
  }

  override suspend fun setUserPermitZone(zone: String?) {
    val existing = getExistingPreferences()
    dao.upsertUserPreferences(existing.copy(rppZone = zone))
    analyticsTracker.setCustomKey("rpp_zone", zone ?: "none")
  }

  private suspend fun getExistingPreferences(): UserPreferencesEntity {
    return dao.getUserPreferences().first() ?: UserPreferencesEntity(id = 1, rppZone = null)
  }

  override suspend fun refreshData(): Boolean = coroutineScope {
    Log.d(TAG, "refreshData: starting")
    // Fetch in parallel
    val parkingRegulationsJob = async { fetchAllParkingRegulations() }
    val sweepingDataJob = async { fetchAllSweepingData() }
    val meterInventoryJob = async { fetchAllMeterInventory() }
    val meterSchedulesJob = async { fetchAllMeterSchedules() }

    val parkingRegulations = parkingRegulationsJob.await()
    val sweepingData = sweepingDataJob.await()
    val meterInventory = meterInventoryJob.await()
    val meterSchedulesRaw = meterSchedulesJob.await()

    Log.d(TAG, "refreshData: fetched ${parkingRegulations.size} parking regulations")
    Log.d(TAG, "refreshData: fetched ${sweepingData.size} sweeping records")
    Log.d(TAG, "refreshData: fetched ${meterInventory.size} meter records")
    Log.d(TAG, "refreshData: fetched ${meterSchedulesRaw.size} meter schedule records")

    if (parkingRegulations.isEmpty() && meterInventory.isEmpty()) {
      Log.w(TAG, "refreshData: no parking data found, aborting")
      return@coroutineScope false
    }

    // Heavy processing on background thread
    val (spots, sweepingSchedules, meterSchedules) =
      withContext(defaultDispatcher) {
        Log.d(TAG, "refreshData: building spatial index...")
        val matcher = CoordinateMatcher(sweepingData)
        Log.d(TAG, "refreshData: spatial index built, starting matching...")

        val spotsList = mutableListOf<ParkingSpotEntity>()
        val sweepingList = mutableListOf<SweepingScheduleEntity>()
        val meterSchedulesList = mutableListOf<MeterScheduleEntity>()

        // Pre-group meter schedules by postId for fast lookup
        val meterSchedulesByPostId = meterSchedulesRaw.groupBy { it.postId }

        // 1. Process standard regulations
        for ((index, regulation) in parkingRegulations.withIndex()) {
          if (index % 1000 == 0) {
            Log.d(TAG, "refreshData: processing regulation $index/${parkingRegulations.size}")
          }

          val geometry = parseGeometry(regulation.shape) ?: continue
          val regulationType = regulation.regulation.toParkingRegulation()
          val sweepingMatch = matcher.findMatch(geometry)
          val firstSchedule = sweepingMatch?.schedules?.firstOrNull()

          val spotId = "reg_${regulation.objectId}"
          val spot =
            ParkingSpotEntity(
              objectId = spotId,
              geometry = geometry,
              streetName = firstSchedule?.streetName?.takeIf { it.isNotBlank() },
              blockLimits = firstSchedule?.limits?.takeIf { it.isNotBlank() },
              neighborhood = regulation.neighborhood,
              regulation = regulationType,
              rppArea = regulation.rppArea1?.takeIf { it.isNotBlank() },
              timeLimitHours = parseTimeLimit(regulation.hrLimit),
              enforcementDays = parseEnforcementDays(regulation.days),
              enforcementStart = parseTime(regulation.hrsBegin)?.let { timeToLocalTime(it) },
              enforcementEnd = parseTime(regulation.hrsEnd)?.let { timeToLocalTime(it) },
              sweepingCnn = sweepingMatch?.cnn,
              sweepingSide = sweepingMatch?.side?.toStreetSide(),
            )

          spotsList.add(spot)
          sweepingMatch?.let { match ->
            addSweepingSchedules(spotId, match.schedules, sweepingList)
          }
        }

        // 2. Process meter inventory (Paid Parking)
        val metersByCnn =
          meterInventory
            .filter { !it.streetSegCtrlnId.isNullOrBlank() }
            .groupBy { it.streetSegCtrlnId!! }

        Log.d(TAG, "refreshData: processing ${metersByCnn.size} metered CNN segments")

        for ((cnn, meters) in metersByCnn) {
          val sweepingMatchesForCnn = matcher.findAllMatchesForCnn(cnn)

          for (match in sweepingMatchesForCnn) {
            val spotId = "meter_${cnn}_${match.side}"

            // Deduplication: Skip if we already have a regulation for this CNN and Side
            if (
              spotsList.any {
                it.sweepingCnn == cnn && it.sweepingSide == match.side.toStreetSide()
              }
            ) {
              continue
            }

            val firstMeter = meters.first()

            val spot =
              ParkingSpotEntity(
                objectId = spotId,
                geometry = match.geometry,
                streetName = match.schedules.firstOrNull()?.streetName ?: firstMeter.streetName,
                blockLimits = match.schedules.firstOrNull()?.limits,
                neighborhood = firstMeter.neighborhood,
                regulation = ParkingRegulation.METERED,
                rppArea = null,
                timeLimitHours = null,
                enforcementDays = null,
                enforcementStart = null,
                enforcementEnd = null,
                sweepingCnn = cnn,
                sweepingSide = match.side.toStreetSide(),
              )

            spotsList.add(spot)
            addSweepingSchedules(spotId, match.schedules, sweepingList)

            // Add Meter Operating Schedules for this segment
            val uniquePostIds = meters.map { it.postId }.toSet()
            val schedulesForThisSpot =
              uniquePostIds.flatMap { meterSchedulesByPostId[it] ?: emptyList() }
            addMeterSchedules(spotId, schedulesForThisSpot, meterSchedulesList)
          }
        }

        Triple(spotsList, sweepingList, meterSchedulesList)
      }

    Log.d(
      TAG,
      "refreshData: saving ${spots.size} spots, ${sweepingSchedules.size} sweeping, " +
        "${meterSchedules.size} meter schedules",
    )
    if (spots.isNotEmpty()) {
      dao.clearAllMeterSchedules()
      dao.clearAllSchedules()
      dao.clearAllSpots()
      dao.insertSpots(spots)
      dao.insertSchedules(sweepingSchedules)
      dao.insertMeterSchedules(meterSchedules)
      Log.d(TAG, "refreshData: saved to database")
    }

    return@coroutineScope true
  }

  private fun addSweepingSchedules(
    spotId: String,
    schedules: List<StreetCleaningResponse>,
    schedulesList: MutableList<SweepingScheduleEntity>,
  ) {
    for (schedule in schedules) {
      val fromHour = (schedule.fromhour.toIntOrNull() ?: 0) % 24
      val toHour = (schedule.tohour.toIntOrNull() ?: 0) % 24

      schedulesList.add(
        SweepingScheduleEntity(
          parkingSpotId = spotId,
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

  @Suppress("LoopWithTooManyJumpStatements")
  private fun addMeterSchedules(
    spotId: String,
    responses: List<MeterScheduleResponse>,
    schedulesList: MutableList<MeterScheduleEntity>,
  ) {
    // Group by unique schedule properties to deduplicate across multiple meters on the same block
    val uniqueSchedules =
      responses.groupBy {
        Triple(it.daysApplied, it.fromTime to it.toTime, it.timeLimit to it.scheduleType)
      }

    for ((_, schedules) in uniqueSchedules) {
      val first = schedules.first()
      val days = parseMeterDays(first.daysApplied)
      val start = parseMeterTime(first.fromTime) ?: continue
      val end = parseMeterTime(first.toTime) ?: continue
      val limit = first.timeLimit?.filter { it.isDigit() }?.toIntOrNull() ?: 0
      val isTow = first.scheduleType?.contains("Tow", ignoreCase = true) == true

      schedulesList.add(
        MeterScheduleEntity(
          parkingSpotId = spotId,
          days = days,
          startTime = start,
          endTime = end,
          timeLimitMinutes = limit,
          isTowZone = isTow,
        )
      )
    }
  }

  private fun parseMeterDays(daysStr: String?): Set<DayOfWeek> {
    if (daysStr == null) return DayOfWeek.values().toSet()
    val days = mutableSetOf<DayOfWeek>()
    val parts = daysStr.split(",")
    for (part in parts) {
      when (part.trim()) {
        "Mo" -> days.add(DayOfWeek.MONDAY)
        "Tu" -> days.add(DayOfWeek.TUESDAY)
        "We" -> days.add(DayOfWeek.WEDNESDAY)
        "Th" -> days.add(DayOfWeek.THURSDAY)
        "Fr" -> days.add(DayOfWeek.FRIDAY)
        "Sa" -> days.add(DayOfWeek.SATURDAY)
        "Su" -> days.add(DayOfWeek.SUNDAY)
      }
    }
    return if (days.isEmpty()) DayOfWeek.values().toSet() else days
  }

  private fun parseMeterTime(timeStr: String?): LocalTime? {
    if (timeStr == null) return null
    return try {
      val parts = timeStr.split(" ")
      val timeParts = parts[0].split(":")
      var hour = timeParts[0].toInt()
      val minute = if (timeParts.size > 1) timeParts[1].toInt() else 0
      val isPm = parts.size > 1 && parts[1].uppercase() == "PM"

      if (isPm && hour < 12) hour += 12
      if (!isPm && hour == 12) hour = 0

      LocalTime(hour % 24, minute % 60)
    } catch (e: NumberFormatException) {
      Log.e(TAG, "parseMeterTime: failed to parse time", e)
      null
    }
  }

  @Suppress("LoopWithTooManyJumpStatements")
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

  @Suppress("LoopWithTooManyJumpStatements")
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

  @Suppress("LoopWithTooManyJumpStatements")
  private suspend fun fetchAllMeterInventory(): List<ParkingMeterResponse> {
    val allMeters = mutableListOf<ParkingMeterResponse>()
    var offset = 0
    val limit = 1000

    while (true) {
      val result = api.getParkingMeterInventory(limit = limit, offset = offset)
      if (result.isFailure) {
        val exception = result.exceptionOrNull()
        exception?.let {
          analyticsTracker.logNonFatal(it, "API failure: fetchAllMeterInventory at offset $offset")
        }
        break
      }

      val batch = result.getOrNull() ?: emptyList()
      if (batch.isEmpty()) break

      allMeters.addAll(batch)
      offset += limit
    }

    return allMeters
  }

  @Suppress("LoopWithTooManyJumpStatements")
  private suspend fun fetchAllMeterSchedules(): List<MeterScheduleResponse> {
    val allSchedules = mutableListOf<MeterScheduleResponse>()
    var offset = 0
    val limit = 1000

    while (true) {
      val result = api.getMeterSchedules(limit = limit, offset = offset)
      if (result.isFailure) {
        val exception = result.exceptionOrNull()
        exception?.let {
          analyticsTracker.logNonFatal(it, "API failure: fetchAllMeterSchedules at offset $offset")
        }
        break
      }

      val batch = result.getOrNull() ?: emptyList()
      if (batch.isEmpty()) break

      allSchedules.addAll(batch)
      offset += limit
    }

    return allSchedules
  }

  private fun ParkingRegulationResponse.isParkable(): Boolean {
    val regulation = this.regulation.toParkingRegulation()
    return regulation.isParkable
  }

  private fun parseGeometry(shape: JsonElement?): Geometry? {
    if (shape == null) return null
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

    return Geometry(type = "LineString", coordinates = coordinates)
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
      timedRestriction =
        spot.timeLimitHours?.let { limitHours ->
          TimedRestriction(
            limitHours = limitHours,
            days = spot.enforcementDays ?: emptySet(),
            startTime = spot.enforcementStart,
            endTime = spot.enforcementEnd,
          )
        },
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
      meterSchedules =
        meterSchedules.map { entity ->
          MeterSchedule(
            days = entity.days,
            startTime = entity.startTime,
            endTime = entity.endTime,
            timeLimitMinutes = entity.timeLimitMinutes,
            isTowZone = entity.isTowZone,
          )
        },
    )
  }
}
