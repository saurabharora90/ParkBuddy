package dev.bongballe.parkbuddy.data.sf.repository

import co.touchlab.kermit.Logger
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
import dev.bongballe.parkbuddy.data.sf.model.StreetCleaningResponse
import dev.bongballe.parkbuddy.data.sf.model.toParkingRegulation
import dev.bongballe.parkbuddy.data.sf.model.toStreetSide
import dev.bongballe.parkbuddy.data.sf.network.SfOpenDataApi
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.MeterSchedule
import dev.bongballe.parkbuddy.model.ParkingRegulation
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.bongballe.parkbuddy.model.TimedRestriction
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.io.IOException
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

  /**
   * Internal context used during data refresh to merge multiple data sources into a single segment.
   */
  private data class UnifiedSegmentContext(
    val cnn: String,
    val side: StreetSide,
    var centerline: Geometry? = null,
    var curbsideGeometry: Geometry? = null,
    var streetName: String? = null,
    var blockLimits: String? = null,
    var neighborhood: String? = null,
    var regulation: ParkingRegulation? = null,
    val rppAreas: MutableSet<String> = mutableSetOf(),
    var timeLimitHours: Int? = null,
    var enforcementDays: Set<DayOfWeek>? = null,
    var enforcementStart: LocalTime? = null,
    var enforcementEnd: LocalTime? = null,
    val sweepingSchedules: MutableList<StreetCleaningResponse> = mutableListOf(),
    val meterSchedules: MutableList<MeterScheduleResponse> = mutableListOf(),
    var originalObjectId: String? = null,
  )

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
    private val log = Logger.withTag("ParkingRepository")

    /** Threshold for matching a regulation or meter to a street centerline segment. */
    private const val MATCHING_THRESHOLD_METERS = 20.0
    private const val API_BATCH_LIMIT = 5000
  }

  override fun getAllSpots(): Flow<List<ParkingSpot>> {
    return dao.getAllSpots().map { entities ->
      log.d { "getAllSpots: mapping ${entities.size} entities" }
      entities.map { it.toDomainModel() }
    }
  }

  override fun getSpotsByZone(zone: String): Flow<List<ParkingSpot>> {
    return dao.getSpotsByZone(zone).map { entities ->
      log.d { "getSpotsByZone($zone): mapping ${entities.size} entities" }
      entities.map { it.toDomainModel() }
    }
  }

  override fun countSpotsByZone(zone: String): Flow<Int> {
    return dao.countSpotsByZone(zone)
  }

  override fun getAllPermitZones(): Flow<List<String>> {
    return dao.getAllRppZonesInternal().map { rppAreaStrings ->
      rppAreaStrings.flatMap { it.split(",") }.filter { it.isNotBlank() }.distinct().sorted()
    }
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

  private data class ProcessingResult(
    val spots: List<ParkingSpotEntity>,
    val sweepingSchedules: List<SweepingScheduleEntity>,
    val meterSchedules: List<MeterScheduleEntity>,
    val hasParkingData: Boolean,
  )

  override suspend fun refreshData(): Boolean = coroutineScope {
    log.d { "refreshData: starting memory-efficient fetch" }

    // 1. Fetch Sweeping Data (The Backbone)
    // We need this entire dataset to build the CoordinateMatcher and unifiedContexts.
    // It's the smallest layer (~35k records) and is the physical street network.
    val sweepingData = fetchAllSweepingData()
    if (sweepingData.isEmpty()) {
      log.w { "refreshData: no sweeping data found, aborting" }
      return@coroutineScope false
    }

    log.d { "refreshData: sweeping data loaded. Building matcher..." }

    // Heavy processing on background thread
    val result =
      withContext(defaultDispatcher) {
        var hasMatchedParking = false
        val matcher = CoordinateMatcher(sweepingData)
        val unifiedContexts = mutableMapOf<Pair<String, StreetSide>, UnifiedSegmentContext>()

        // Seed contexts from Sweeping Data
        for (sweeping in sweepingData) {
          val side = sweeping.cnnRightLeft.toStreetSide()
          val key = sweeping.cnn to side
          val context =
            unifiedContexts.getOrPut(key) { UnifiedSegmentContext(cnn = sweeping.cnn, side = side) }

          context.sweepingSchedules.add(sweeping)
          if (context.centerline == null) {
            context.centerline = parseGeometry(sweeping.geometry)
            context.streetName = sweeping.streetName
            context.blockLimits = sweeping.limits
          }
        }

        // 2. Fetch and Match Parking Regulations in Batches
        log.d { "refreshData: fetching parking regulations in batches" }
        fetchInBatches(
          fetch = { limit, offset -> api.getParkingRegulations(limit = limit, offset = offset) },
          process = { batch ->
            for (regulation in batch) {
              val poly = parseGeometry(regulation.shape) ?: continue
              val overlappingSegments = matcher.matchPolyline(poly, MATCHING_THRESHOLD_METERS)

              for (match in overlappingSegments) {
                val key = match.cnn to match.side
                val context = unifiedContexts[key] ?: continue

                hasMatchedParking = true
                // If the regulation is higher resolution, use its geometry
                context.curbsideGeometry = poly

                context.regulation = regulation.regulation.toParkingRegulation()
                regulation.rppArea1?.takeIf { it.isNotBlank() }?.let { context.rppAreas.add(it) }
                regulation.rppArea2?.takeIf { it.isNotBlank() }?.let { context.rppAreas.add(it) }
                regulation.rppArea3?.takeIf { it.isNotBlank() }?.let { context.rppAreas.add(it) }
                context.neighborhood = regulation.neighborhood
                context.timeLimitHours = parseTimeLimit(regulation.hrLimit)
                context.enforcementDays = parseEnforcementDays(regulation.days)
                context.enforcementStart =
                  parseTime(regulation.hrsBegin)?.let { timeToLocalTime(it) }
                context.enforcementEnd = parseTime(regulation.hrsEnd)?.let { timeToLocalTime(it) }
                context.originalObjectId = regulation.objectId
              }
            }
          },
        )

        // 3. Fetch all Meter Schedules (Needed for Inventory matching)
        // Grouped by Post ID to keep memory usage manageable.
        log.d { "refreshData: fetching meter schedules" }
        val meterSchedulesByPostId = mutableMapOf<String, MutableList<MeterScheduleResponse>>()
        fetchInBatches(
          fetch = { limit, offset -> api.getMeterSchedules(limit = limit, offset = offset) },
          process = { batch ->
            for (schedule in batch) {
              val postId = schedule.postId ?: continue
              meterSchedulesByPostId.getOrPut(postId) { mutableListOf() }.add(schedule)
            }
          },
        )

        // 4. Fetch and Match Meter Inventory in Batches
        log.d { "refreshData: fetching meter inventory in batches" }
        fetchInBatches(
          fetch = { limit, offset -> api.getParkingMeterInventory(limit = limit, offset = offset) },
          process = { batch ->
            val metersByCnn =
              batch
                .filter { !it.streetSegCtrlnId.isNullOrBlank() }
                .groupBy { it.streetSegCtrlnId.orEmpty() }

            for ((cnn, meters) in metersByCnn) {
              val matches = matcher.findAllMatchesForCnn(cnn)
              for (match in matches) {
                val key = cnn to match.side
                val context = unifiedContexts[key] ?: continue

                hasMatchedParking = true
                if (context.regulation == null) {
                  context.regulation = ParkingRegulation.METERED
                  context.neighborhood = meters.firstOrNull()?.neighborhood
                  context.streetName = context.streetName ?: meters.firstOrNull()?.streetName
                }

                val postIds = meters.map { it.postId }.toSet()
                postIds.forEach { postId ->
                  meterSchedulesByPostId[postId]?.let { context.meterSchedules.addAll(it) }
                }
              }
            }
          },
        )

        if (!hasMatchedParking) {
          return@withContext ProcessingResult(emptyList(), emptyList(), emptyList(), false)
        }

        // Finalize Entities
        val spotsList = mutableListOf<ParkingSpotEntity>()
        val sweepingList = mutableListOf<SweepingScheduleEntity>()
        val meterSchedulesList = mutableListOf<MeterScheduleEntity>()

        for (context in unifiedContexts.values) {
          // Skip segments that only have sweeping data but no parking regulation or meters.
          if (context.regulation == null && context.meterSchedules.isEmpty()) continue

          // RESOLVE Geometry: Prioritize Reg, then offset Centerline
          val finalGeometry =
            when {
              context.curbsideGeometry != null -> context.curbsideGeometry
              context.centerline != null ->
                CoordinateMatcher.offsetGeometry(checkNotNull(context.centerline), context.side)

              else -> null
            } ?: continue

          val spotId = "cnn_${context.cnn}_${context.side.name}"

          val spot =
            ParkingSpotEntity(
              objectId = spotId,
              geometry = finalGeometry,
              streetName = context.streetName,
              blockLimits = context.blockLimits,
              neighborhood = context.neighborhood,
              regulation = checkNotNull(context.regulation),
              rppAreas = context.rppAreas.toList().sorted(),
              timeLimitHours = context.timeLimitHours,
              enforcementDays = context.enforcementDays,
              enforcementStart = context.enforcementStart,
              enforcementEnd = context.enforcementEnd,
              sweepingCnn = context.cnn,
              sweepingSide = context.side,
            )

          spotsList.add(spot)
          addSweepingSchedules(spotId, context.sweepingSchedules, sweepingList)
          addMeterSchedules(spotId, context.meterSchedules, meterSchedulesList)
        }

        ProcessingResult(spotsList, sweepingList, meterSchedulesList, hasMatchedParking)
      }

    log.d { "refreshData: saving ${result.spots.size} segments to DB" }
    if (result.hasParkingData && result.spots.isNotEmpty()) {
      dao.clearAllMeterSchedules()
      dao.clearAllSchedules()
      dao.clearAllSpots()
      dao.insertSpots(result.spots)
      dao.insertSchedules(result.sweepingSchedules)
      dao.insertMeterSchedules(result.meterSchedules)
      log.d { "refreshData: sync complete" }
    }

    return@coroutineScope result.hasParkingData
  }

  private suspend fun <T> fetchInBatches(
    fetch: suspend (limit: Int, offset: Int) -> List<T>,
    process: suspend (List<T>) -> Unit,
  ) {
    var offset = 0
    while (true) {
      val batch =
        try {
          fetch(API_BATCH_LIMIT, offset)
        } catch (e: Exception) {
          log.e(e) { "fetchInBatches: API call failed at offset $offset" }
          break
        }
      if (batch.isEmpty()) break
      process(batch)
      offset += API_BATCH_LIMIT
    }
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

  private fun addMeterSchedules(
    spotId: String,
    responses: List<MeterScheduleResponse>,
    schedulesList: MutableList<MeterScheduleEntity>,
  ) {
    // Phase 1: Deduplicate identical raw API responses (same string fields across meter posts).
    val uniqueSchedules =
      responses.groupBy {
        Triple(it.daysApplied, it.fromTime to it.toTime, it.timeLimit to it.scheduleType)
      }

    // Phase 2: Parse into (days, start, end, limit, isTow) tuples.
    data class Parsed(
      val days: Set<DayOfWeek>,
      val start: LocalTime,
      val end: LocalTime,
      val limit: Int,
      val isTow: Boolean,
    )

    val parsed = mutableListOf<Parsed>()
    for ((_, schedules) in uniqueSchedules) {
      val first = schedules.first()
      val days = parseMeterDays(first.daysApplied)
      val start = parseMeterTime(first.fromTime) ?: continue
      val end = parseMeterTime(first.toTime) ?: continue
      val limit = first.timeLimit?.filter { it.isDigit() }?.toIntOrNull() ?: 0
      val isTow = first.scheduleType?.contains("Tow", ignoreCase = true) == true
      parsed.add(Parsed(days, start, end, limit, isTow))
    }

    // Phase 3: Merge schedules that share the same time window, limit, and tow status
    // by unioning their day sets. Then drop any schedule whose days are a strict subset
    // of another with the same window and a <= limit (the broader one is more restrictive
    // or equally restrictive, so the subset adds no information).
    val merged =
      parsed
        .groupBy { Triple(it.start, it.end, it.limit to it.isTow) }
        .flatMap { (key, group) ->
          val (start, end, limitTow) = key
          val (limit, isTow) = limitTow
          val unionDays = group.fold(emptySet<DayOfWeek>()) { acc, p -> acc + p.days }
          listOf(Parsed(unionDays, start, end, limit, isTow))
        }
        .toMutableList()

    // Remove schedules whose days are a strict subset of another with the same window
    // and equal-or-shorter time limit (the superset already covers those days).
    merged.removeAll { candidate ->
      merged.any { other ->
        other !== candidate &&
          other.start == candidate.start &&
          other.end == candidate.end &&
          other.isTow == candidate.isTow &&
          other.limit <= candidate.limit &&
          other.days.containsAll(candidate.days) &&
          other.days.size > candidate.days.size
      }
    }

    for (schedule in merged) {
      schedulesList.add(
        MeterScheduleEntity(
          parkingSpotId = spotId,
          days = schedule.days,
          startTime = schedule.start,
          endTime = schedule.end,
          timeLimitMinutes = schedule.limit,
          isTowZone = schedule.isTow,
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
      log.e(e) { "parseMeterTime: invalid time format" }
      null
    }
  }

  private suspend fun fetchAllSweepingData(): List<StreetCleaningResponse> {
    val allSweeping = mutableListOf<StreetCleaningResponse>()
    var offset = 0

    while (true) {
      val batch =
        try {
          api.getStreetCleaningData(limit = API_BATCH_LIMIT, offset = offset)
        } catch (e: IOException) {
          log.e(e) { "fetchAllSweepingData: API call failed at offset $offset" }
          analyticsTracker.logNonFatal(e, "API failure: fetchAllSweepingData at offset $offset")
          break
        }
      if (batch.isEmpty()) break
      allSweeping.addAll(batch.filter { it.cnn.isNotEmpty() && it.geometry != null })
      offset += API_BATCH_LIMIT
    }
    return allSweeping
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
    return if (hour >= 24) {
      LocalTime(23, 59)
    } else {
      LocalTime(hour, minute.coerceIn(0, 59))
    }
  }

  private fun PopulatedParkingSpot.toDomainModel(): ParkingSpot {
    // SF publishes parking rules in two datasets: a regulation layer (timedRestriction)
    // and a meter schedule layer. When both exist for the same spot, the meter schedules
    // are strictly more granular, so drop the regulation-sourced timedRestriction to
    // avoid double-reporting the same rule.
    val hasMeters = meterSchedules.isNotEmpty()

    return ParkingSpot(
      objectId = spot.objectId,
      geometry = spot.geometry,
      streetName = spot.streetName,
      blockLimits = spot.blockLimits,
      neighborhood = spot.neighborhood,
      regulation = spot.regulation,
      rppAreas = spot.rppAreas,
      timedRestriction =
        if (hasMeters) null
        else
          spot.timeLimitHours?.let { limitHours ->
            TimedRestriction(
              limitHours = limitHours,
              days = spot.enforcementDays.orEmpty(),
              startTime = spot.enforcementStart,
              endTime = spot.enforcementEnd,
            )
          },
      sweepingCnn = spot.sweepingCnn,
      sweepingSide = spot.sweepingSide,
      sweepingSchedules =
        schedules
          .map { schedule ->
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
          }
          .sortedWith(compareBy({ it.weekday.ordinal }, { it.fromHour })),
      meterSchedules =
        meterSchedules
          .map { entity ->
            MeterSchedule(
              days = entity.days,
              startTime = entity.startTime,
              endTime = entity.endTime,
              timeLimitMinutes = entity.timeLimitMinutes,
              isTowZone = entity.isTowZone,
            )
          }
          .sortedWith(
            compareBy(
              { schedule -> schedule.days.minByOrNull { it.ordinal }?.ordinal ?: Int.MAX_VALUE },
              { it.startTime },
            )
          ),
    )
  }
}
