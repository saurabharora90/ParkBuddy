package dev.bongballe.parkbuddy.data.sf.repository

import co.touchlab.kermit.Logger
import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.data.repository.utils.TimelineResolver
import dev.bongballe.parkbuddy.data.sf.CoordinateMatcher
import dev.bongballe.parkbuddy.data.sf.DayParser
import dev.bongballe.parkbuddy.data.sf.TimeParser
import dev.bongballe.parkbuddy.data.sf.database.ParkingDao
import dev.bongballe.parkbuddy.data.sf.database.entity.ParkingSpotEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.SweepingScheduleEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.UserPreferencesEntity
import dev.bongballe.parkbuddy.data.sf.database.model.PopulatedParkingSpot
import dev.bongballe.parkbuddy.data.sf.model.MeterScheduleResponse
import dev.bongballe.parkbuddy.data.sf.model.ParkingRegulation
import dev.bongballe.parkbuddy.data.sf.model.StreetCleaningResponse
import dev.bongballe.parkbuddy.data.sf.model.toParkingRegulation
import dev.bongballe.parkbuddy.data.sf.model.toStreetSide
import dev.bongballe.parkbuddy.data.sf.network.SfOpenDataApi
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.IntervalSource
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ProhibitionReason
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.SweepingSchedule
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
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * SF-specific implementation of [ParkingRepository].
 *
 * ## Data Sync Pipeline (refreshData)
 *
 * Merges four SF Open Data APIs into a unified street segment model:
 * ```
 *   ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  ┌──────────────────┐
 *   │  Sweeping     │  │  Regulations  │  │  Meter        │  │  Meter           │
 *   │  (geometry    │  │  (time limits │  │  Inventory    │  │  Schedules       │
 *   │   backbone)   │  │   RPP zones)  │  │  (post IDs)   │  │  (hours, limits) │
 *   └──────┬───────┘  └──────┬───────┘  └──────┬────────┘  └──────┬───────────┘
 *          │                 │                  │                  │
 *          ▼                 ▼                  ▼                  ▼
 *   ┌─────────────────────────────────────────────────────────────────────┐
 *   │                    UnifiedSegmentContext                           │
 *   │  Key: (CNN, StreetSide)                                           │
 *   │  Accumulates: geometry, street name, regulations, meter schedules │
 *   └──────────────────────────────┬──────────────────────────────────────┘
 *                                  │
 *                                  ▼
 *   ┌─────────────────────────────────────────────────────────────────────┐
 *   │                      TimelineResolver                              │
 *   │  Flattens overlapping rules into non-overlapping ParkingIntervals  │
 *   │  Priority: FORBIDDEN > RESTRICTED > METERED > LIMITED > OPEN       │
 *   └──────────────────────────────┬──────────────────────────────────────┘
 *                                  │
 *                                  ▼
 *   ┌─────────────────────────────────────────────────────────────────────┐
 *   │  ParkingSpotEntity (timeline JSON) + SweepingScheduleEntity        │
 *   │  Stored in Room, consumed by UI and evaluator                      │
 *   └────────────────────────────────────────────────────────────────────┘
 * ```
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ParkingRepositoryImpl(
  private val dao: ParkingDao,
  private val api: SfOpenDataApi,
  @WithDispatcherType(DispatcherType.DEFAULT) private val defaultDispatcher: CoroutineDispatcher,
) : ParkingRepository {

  /**
   * Accumulator for all data layers for a single (CNN, side) street segment.
   *
   * During sync, each API response contributes data to the appropriate context. After all data is
   * collected, the context is resolved into a [ParkingSpotEntity] with a pre-computed timeline.
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
    /** Raw parsed regulation intervals (before timeline resolution). */
    val candidateIntervals: MutableList<ParkingInterval> = mutableListOf(),
    val sweepingSchedules: MutableList<StreetCleaningResponse> = mutableListOf(),
    /** Raw meter schedule responses (before parsing into intervals). */
    val meterSchedules: MutableList<MeterScheduleResponse> = mutableListOf(),
    /** True if physical meters exist on this CNN (even if no schedules were found). */
    var hasPhysicalMeters: Boolean = false,
  )

  /**
   * Regulation priority ranking. Higher rank wins when multiple regulations match the same segment
   * via geometry overlap.
   */
  private fun ParkingRegulation.rank(): Int =
    when (this) {
      ParkingRegulation.TIME_LIMITED -> 100
      ParkingRegulation.RPP_ONLY -> 95
      ParkingRegulation.PAY_OR_PERMIT -> 90
      ParkingRegulation.PAID_PLUS_PERMIT -> 85
      ParkingRegulation.METERED -> 80
      ParkingRegulation.LOADING_ZONE -> 50
      ParkingRegulation.COMMERCIAL_ONLY -> 40
      ParkingRegulation.GOVERNMENT_ONLY -> 30
      ParkingRegulation.NO_OVERNIGHT -> 25
      ParkingRegulation.NO_OVERSIZED -> 20
      ParkingRegulation.NO_PARKING -> 10
      ParkingRegulation.NO_STOPPING -> 5
      ParkingRegulation.UNKNOWN -> 0
    }

  companion object {
    private val log = Logger.withTag("ParkingRepository")

    /**
     * Maximum distance (in meters) for matching a regulation polyline to a sweeping centerline.
     * Compromise between accuracy and coverage:
     * - 20m (original): caused regulation leakage at dense intersections
     * - 10m (too tight): missed valid matches where regulation polylines are offset from centerline
     * - 15m: catches curb-offset regulations while limiting intersection leakage
     */
    private const val MATCHING_THRESHOLD_METERS = 15.0
    private const val API_BATCH_LIMIT = 5000
  }

  // ── Public API (ParkingRepository interface) ──

  override fun getAllSpots(): Flow<List<ParkingSpot>> =
    dao.getAllSpots().map { it.map { e -> e.toDomainModel() } }

  override fun getSpotsByZone(zone: String): Flow<List<ParkingSpot>> =
    dao.getSpotsByZone(zone).map { it.map { e -> e.toDomainModel() } }

  override fun countSpotsByZone(zone: String): Flow<Int> = dao.countSpotsByZone(zone)

  override fun getAllPermitZones(): Flow<List<String>> =
    dao.getAllRppZonesInternal().map {
      it.flatMap { s -> s.split(",") }.filter { s -> s.isNotBlank() }.distinct().sorted()
    }

  override fun getUserPermitZone(): Flow<String?> = dao.getUserPreferences().map { it?.rppZone }

  override suspend fun setUserPermitZone(zone: String?) {
    val existing = dao.getUserPreferences().first() ?: UserPreferencesEntity(1, null)
    dao.upsertUserPreferences(existing.copy(rppZone = zone))
  }

  // ── Data Sync ──

  private data class ProcessingResult(
    val spots: List<ParkingSpotEntity>,
    val sweepingSchedules: List<SweepingScheduleEntity>,
    val hasParkingData: Boolean,
  )

  /**
   * Fetches all four SF data sources, merges them by (CNN, side), resolves overlapping rules into a
   * timeline via [TimelineResolver], and stores the result in Room.
   */
  override suspend fun refreshData(): Boolean = coroutineScope {
    log.d { "refreshData: starting sync" }
    val sweepingData = fetchAllSweepingData()
    if (sweepingData.isEmpty()) return@coroutineScope false

    val result =
      withContext(defaultDispatcher) {
        var hasMatchedParking = false
        val matcher = CoordinateMatcher(sweepingData)
        val unifiedContexts = mutableMapOf<Pair<String, StreetSide>, UnifiedSegmentContext>()

        // ── Step 1: Build segment backbone from sweeping data ──
        // Each sweeping record seeds a UnifiedSegmentContext keyed by (CNN, side).
        // Sweeping geometry becomes the street centerline for coordinate matching.
        for (s in sweepingData) {
          val side = s.cnnRightLeft.toStreetSide()
          val context =
            unifiedContexts.getOrPut(s.cnn to side) { UnifiedSegmentContext(s.cnn, side) }
          context.sweepingSchedules.add(s)
          if (context.centerline == null) {
            context.centerline = parseGeometry(s.geometry)
            context.streetName = s.streetName
            context.blockLimits = s.limits
          }
        }

        // ── Steps 2-5: Fetch all data sources in parallel ──
        // Fetch regulations, meter schedules, meter inventory,
        // and tow zones concurrently. Processing (which mutates unifiedContexts) stays sequential.
        val regulationsDeferred = async { fetchAll { l, o -> api.getParkingRegulations(l, o) } }
        val meterSchedulesDeferred = async { fetchAll { l, o -> api.getMeterSchedules(l, o) } }
        val meterInventoryDeferred = async {
          fetchAll { l, o -> api.getParkingMeterInventory(l, o) }
        }
        val towZonesDeferred = async { fetchAll { l, o -> api.getTowAwayZones(l, o) } }

        val regulations = regulationsDeferred.await()
        val allMeterSchedules = meterSchedulesDeferred.await()
        val meterInventory = meterInventoryDeferred.await()
        val towZones = towZonesDeferred.await()

        // ── Process regulations (sequential, mutates unifiedContexts) ──
        for (reg in regulations) {
          val poly = parseGeometry(reg.shape) ?: continue
          val overlaps = matcher.matchPolyline(poly, MATCHING_THRESHOLD_METERS)

          val contexts =
            if (overlaps.isEmpty()) {
              val vCnn = "reg_${reg.objectId}"
              val key = vCnn to StreetSide.RIGHT
              listOf(
                unifiedContexts.getOrPut(key) {
                  UnifiedSegmentContext(vCnn, StreetSide.RIGHT).apply {
                    curbsideGeometry = poly
                    val coord = poly.coordinates.firstOrNull()
                    if (coord != null && coord.size >= 2) {
                      matcher.findClosestSegment(coord[1], coord[0])?.let { closest ->
                        this.streetName = closest.streetName
                        this.neighborhood = reg.neighborhood ?: closest.neighborhood
                      } ?: run { this.streetName = "Unknown Street" }
                    } else {
                      this.streetName = "Unknown Street"
                    }
                  }
                }
              )
            } else overlaps.mapNotNull { unifiedContexts[it.cnn to it.side] }

          for (ctx in contexts) {
            hasMatchedParking = true
            if (ctx.curbsideGeometry == null) ctx.curbsideGeometry = poly

            val incoming = reg.regulation.toParkingRegulation()
            val current = ctx.regulation
            if (current == null || incoming.rank() > current.rank()) ctx.regulation = incoming

            reg.rppArea1?.takeIf { it.isNotBlank() && it != "0" }?.let { ctx.rppAreas.add(it) }
            reg.rppArea2?.takeIf { it.isNotBlank() && it != "0" }?.let { ctx.rppAreas.add(it) }
            reg.rppArea3?.takeIf { it.isNotBlank() && it != "0" }?.let { ctx.rppAreas.add(it) }
            reg.exceptions?.let { e ->
              Regex("Except Area ([A-Z])", RegexOption.IGNORE_CASE).findAll(e).forEach {
                ctx.rppAreas.add(it.groupValues[1].uppercase())
              }
            }
            ctx.neighborhood = reg.neighborhood ?: ctx.neighborhood

            val limit = TimeParser.parseTimeLimit(reg.hrLimit) ?: 0
            val days = DayParser.parseRegulationDays(reg.days)
            val rawStart = TimeParser.parseRegulationTime(reg.hrsBegin) ?: continue
            val rawEnd = TimeParser.parseRegulationTime(reg.hrsEnd) ?: continue
            val (start, end) = TimeParser.normalizeWindow(rawStart, rawEnd)

            val intervalType = regulationToIntervalType(incoming, limit) ?: continue
            ctx.candidateIntervals.add(
              ParkingInterval(
                type = intervalType,
                days = days,
                startTime = start,
                endTime = end,
                exemptPermitZones = permitExemptZonesFor(intervalType, ctx.rppAreas.toList()),
                source = IntervalSource.REGULATION,
              )
            )
          }
        }

        // ── Process meter schedules: index by post_id ──
        val meterSchedulesByPostId = mutableMapOf<String, MutableList<MeterScheduleResponse>>()
        for (s in allMeterSchedules) {
          if (DayParser.parseMeterDays(s.daysApplied) == null) continue
          s.postId?.let { pid -> meterSchedulesByPostId.getOrPut(pid) { mutableListOf() }.add(s) }
        }

        // ── Process meter inventory: match to segments by CNN ──
        val filteredInventory =
          meterInventory.filter {
            !it.streetSegCtrlnId.isNullOrBlank() && it.activeMeterFlag != "L"
          }
        val byCnn = filteredInventory.groupBy { checkNotNull(it.streetSegCtrlnId) }
        for ((cnn, meters) in byCnn) {
          val overlaps = matcher.findAllMatchesForCnn(cnn)
          val contexts =
            if (overlaps.isEmpty()) {
              val vCnn = "meter_$cnn"
              val geometry = buildGeometryFromMeters(meters)
              listOf(
                unifiedContexts.getOrPut(vCnn to StreetSide.RIGHT) {
                  val f = meters.first()
                  UnifiedSegmentContext(vCnn, StreetSide.RIGHT).apply {
                    curbsideGeometry = geometry
                    streetName = f.streetName ?: "Unknown Street"
                    neighborhood = f.neighborhood
                  }
                }
              )
            } else overlaps.mapNotNull { unifiedContexts[cnn to it.side] }

          for (ctx in contexts) {
            hasMatchedParking = true
            ctx.hasPhysicalMeters = true
            val currentReg = ctx.regulation
            if (currentReg == null || ParkingRegulation.METERED.rank() > currentReg.rank())
              ctx.regulation = ParkingRegulation.METERED
            meters
              .mapNotNull { it.postId }
              .forEach { pid -> meterSchedulesByPostId[pid]?.let { ctx.meterSchedules.addAll(it) } }
          }
        }

        // ── Process tow-away zones ──
        for (tow in towZones) {
          if (tow.cnn.isEmpty()) continue
          val side =
            when (tow.side.lowercase()) {
              "left" -> StreetSide.LEFT
              "right" -> StreetSide.RIGHT
              else -> continue
            }
          val ctx = unifiedContexts[tow.cnn to side] ?: continue

          parseTowWindow(tow.tow1Days, tow.tow1Start, tow.tow1End)?.let {
            ctx.candidateIntervals.add(it)
            hasMatchedParking = true
          }

          parseTowWindow(tow.tow2Days, tow.tow2Start, tow.tow2End)?.let {
            ctx.candidateIntervals.add(it)
          }
        }

        // ── Step 6: Resolve each segment's timeline and build entities ──
        val spots = mutableListOf<ParkingSpotEntity>()
        val sweeping = mutableListOf<SweepingScheduleEntity>()

        for (ctx in unifiedContexts.values) {
          if (ctx.regulation == null && ctx.meterSchedules.isEmpty()) continue
          val geom =
            when {
              ctx.curbsideGeometry != null -> ctx.curbsideGeometry
              ctx.centerline != null ->
                CoordinateMatcher.offsetGeometry(checkNotNull(ctx.centerline), ctx.side)

              else -> null
            } ?: continue

          val sId = "cnn_${ctx.cnn}_${ctx.side.name}"

          // Build sweeping schedule entities (kept separate for week-of-month logic)
          addSweepingSchedules(sId, ctx.sweepingSchedules, sweeping)

          // Convert raw meter schedules to candidate ParkingIntervals
          var meterIntervals =
            parseMeterSchedulesToIntervals(ctx.meterSchedules, ctx.rppAreas.toList())

          // Fallback: meters physically exist but the schedules API has no data for them.
          // Inject a default Mon-Sat 7AM-6PM metered interval (the most common SF pattern)
          // with timeLimitMinutes=0 meaning "pay at meter, check meter for actual limit."
          if (meterIntervals.isEmpty() && ctx.hasPhysicalMeters) {
            meterIntervals =
              listOf(
                ParkingInterval(
                  type = IntervalType.Metered(timeLimitMinutes = 0),
                  days = DayParser.parseRegulationDays("M-Sa"),
                  startTime = LocalTime(7, 0),
                  endTime = LocalTime(18, 0),
                  exemptPermitZones = ctx.rppAreas.toList(),
                  source = IntervalSource.METER,
                )
              )
          }

          val allCandidates = ctx.candidateIntervals + meterIntervals

          // Resolve all overlapping rules into a clean, non-overlapping timeline.
          // The resolver uses priority: FORBIDDEN > RESTRICTED > METERED > LIMITED > OPEN.
          // Within the same tier, shorter time limit wins (safer for the user).
          val timeline = TimelineResolver.resolve(allCandidates)

          spots.add(
            ParkingSpotEntity(
              sId,
              geom,
              ctx.streetName,
              ctx.blockLimits,
              ctx.neighborhood,
              ctx.rppAreas.toList().sorted(),
              timeline,
              ctx.cnn,
              ctx.side,
            )
          )
        }
        ProcessingResult(spots, sweeping, hasMatchedParking)
      }

    if (result.hasParkingData) {
      dao.clearAllSchedules()
      dao.clearAllSpots()
      dao.insertSpots(result.spots)
      dao.insertSchedules(result.sweepingSchedules)
    }
    result.hasParkingData
  }

  // ── Meter schedule parsing ──

  /**
   * Converts raw [MeterScheduleResponse]s into [ParkingInterval] candidates for the resolver.
   *
   * Handles deduplication (multiple physical meters on same CNN with identical schedules), midnight
   * normalization (00:00-00:00 = full day), tow zone detection, and commercial color rule
   * classification.
   */
  private fun parseMeterSchedulesToIntervals(
    responses: List<MeterScheduleResponse>,
    rppAreas: List<String>,
  ): List<ParkingInterval> {
    data class ParsedMeter(
      val days: Set<kotlinx.datetime.DayOfWeek>,
      val start: LocalTime,
      val end: LocalTime,
      val limitMinutes: Int,
      val isTow: Boolean,
      val isCommercial: Boolean,
    )

    val parsed =
      responses.mapNotNull { r ->
        val days = DayParser.parseMeterDays(r.daysApplied) ?: return@mapNotNull null
        val rawStart = TimeParser.parseMeterTime(r.fromTime) ?: return@mapNotNull null
        val rawEnd = TimeParser.parseMeterTime(r.toTime) ?: return@mapNotNull null
        val (start, end) = TimeParser.normalizeWindow(rawStart, rawEnd)
        // Clamp meaningless time limits: if the limit >= the enforcement window duration,
        // it's effectively "no cap, just pay." Set to 0 so downstream (UI, evaluator) treats
        // it as metered-with-no-limit rather than showing "Max 24 hrs" in a 13-hour window.
        val rawLimit = r.timeLimit?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val windowMinutes = windowDurationMinutes(start, end)
        val limitMinutes = if (rawLimit >= windowMinutes) 0 else rawLimit
        val isTow = r.scheduleType?.contains("Tow", true) == true
        // Check if this is a commercial/restricted meter by its color rule.
        // Must match the color prefix BEFORE the dash to avoid false positives:
        // "Grey - General metered parking" contains "red" in "metered" but is NOT commercial.
        val colorPrefix = r.appliedColorRule?.substringBefore("-")?.trim()?.lowercase()
        val isCommercial = colorPrefix in setOf("yellow", "red", "orange")

        ParsedMeter(days, start, end, limitMinutes, isTow, isCommercial)
      }

    // Dedup: group by all fields and take first (30+ physical meters on same CNN have same
    // schedule)
    val deduped =
      parsed
        .groupBy { listOf(it.days, it.start, it.end, it.limitMinutes, it.isTow, it.isCommercial) }
        .map { (_, group) -> group.first() }

    return deduped.map { m ->
      val type =
        when {
          m.isTow -> IntervalType.Forbidden(ProhibitionReason.TOW_AWAY)
          m.isCommercial -> IntervalType.Restricted(ProhibitionReason.COMMERCIAL)
          else -> IntervalType.Metered(m.limitMinutes)
        }
      ParkingInterval(
        type = type,
        days = m.days,
        startTime = m.start,
        endTime = m.end,
        exemptPermitZones = permitExemptZonesFor(type, rppAreas),
        source = if (m.isTow) IntervalSource.TOW else IntervalSource.METER,
      )
    }
  }

  /** Duration of an enforcement window in minutes. Handles overnight wrap (e.g. 10PM-6AM = 480). */
  private fun windowDurationMinutes(start: LocalTime, end: LocalTime): Int {
    val s = start.hour * 60 + start.minute
    val e = end.hour * 60 + end.minute
    return if (e > s) e - s else (1440 - s + e)
  }

  // ── Regulation to IntervalType mapping ──

  /**
   * Maps a [ParkingRegulation] to an [IntervalType] for the timeline resolver.
   *
   * Returns null for informational regulations that shouldn't appear in the timeline (e.g.,
   * NO_OVERSIZED applies only to large vehicles, not typical passenger cars).
   */
  private fun regulationToIntervalType(reg: ParkingRegulation, limitMinutes: Int): IntervalType? =
    when (reg) {
      ParkingRegulation.TIME_LIMITED ->
        if (limitMinutes > 0) IntervalType.Limited(limitMinutes) else null

      ParkingRegulation.PAY_OR_PERMIT,
      ParkingRegulation.PAID_PLUS_PERMIT ->
        if (limitMinutes > 0) IntervalType.Limited(limitMinutes) else null
      // RPP-only streets: even with no numeric limit, the regulation itself IS the restriction.
      // Without a permit, you can't park here at all during enforcement hours.
      ParkingRegulation.RPP_ONLY ->
        if (limitMinutes > 0) IntervalType.Limited(limitMinutes)
        else IntervalType.Restricted(ProhibitionReason.RESIDENTIAL_PERMIT)

      ParkingRegulation.METERED -> IntervalType.Metered(limitMinutes)
      ParkingRegulation.COMMERCIAL_ONLY -> IntervalType.Restricted(ProhibitionReason.COMMERCIAL)
      ParkingRegulation.LOADING_ZONE -> IntervalType.Restricted(ProhibitionReason.LOADING_ZONE)
      ParkingRegulation.NO_PARKING -> IntervalType.Forbidden(ProhibitionReason.NO_PARKING)
      ParkingRegulation.NO_STOPPING -> IntervalType.Forbidden(ProhibitionReason.NO_STOPPING)
      ParkingRegulation.NO_OVERNIGHT -> IntervalType.Forbidden(ProhibitionReason.NO_OVERNIGHT)
      ParkingRegulation.GOVERNMENT_ONLY -> null // Doesn't affect regular passenger vehicles
      ParkingRegulation.NO_OVERSIZED -> null // Doesn't affect regular passenger vehicles
      ParkingRegulation.UNKNOWN -> null
    }

  /**
   * RPP zones only grant exemptions for time-based restrictions (limited, metered) and RPP-only
   * zones. Commercial, loading zone, tow-away, and no-parking restrictions are vehicle-type or
   * absolute prohibitions that residential permits cannot override.
   */
  private fun permitExemptZonesFor(type: IntervalType, rppAreas: List<String>): List<String> =
    when (type) {
      is IntervalType.Limited,
      is IntervalType.Metered -> rppAreas
      is IntervalType.Restricted ->
        if (type.reason == ProhibitionReason.RESIDENTIAL_PERMIT) rppAreas else emptyList()
      is IntervalType.Forbidden,
      is IntervalType.Open -> emptyList()
    }

  // ── Sweeping schedule entity creation ──

  /**
   * Creates [SweepingScheduleEntity]s from raw sweeping responses.
   *
   * Deduplicates by all fields to handle the (common) case where the API returns duplicate records
   * for the same sweeping window.
   */
  /**
   * Parses a tow-away window from the tow zone dataset into a [ParkingInterval].
   *
   * Returns null if the window is empty (tow2start/tow2end = "0") or days are missing.
   */
  private fun parseTowWindow(
    daysStr: String?,
    startStr: String?,
    endStr: String?,
  ): ParkingInterval? {
    if (daysStr.isNullOrBlank()) return null
    val start = TimeParser.parseRegulationTime(startStr) ?: return null
    val end = TimeParser.parseRegulationTime(endStr) ?: return null
    // "0" means no second window
    if (start == LocalTime(0, 0) && end == LocalTime(0, 0)) return null
    val days = DayParser.parseMeterDays(daysStr) ?: return null
    return ParkingInterval(
      type = IntervalType.Forbidden(ProhibitionReason.TOW_AWAY),
      days = days,
      startTime = start,
      endTime = end,
      source = IntervalSource.TOW,
    )
  }

  private fun addSweepingSchedules(
    sId: String,
    schs: List<StreetCleaningResponse>,
    out: MutableList<SweepingScheduleEntity>,
  ) {
    schs
      .distinctBy {
        listOf(
          it.weekday,
          it.fromhour,
          it.tohour,
          it.servicedOnFirstWeekOfMonth,
          it.servicedOnSecondWeekOfMonth,
          it.servicedOnThirdWeekOfMonth,
          it.servicedOnFourthWeekOfMonth,
          it.servicedOnFifthWeekOfMonth,
          it.servicedOnHolidays,
        )
      }
      .forEach { s ->
        out.add(
          SweepingScheduleEntity(
            sId,
            s.weekday,
            (s.fromhour.toIntOrNull() ?: 0) % 24,
            (s.tohour.toIntOrNull() ?: 0) % 24,
            s.servicedOnFirstWeekOfMonth,
            s.servicedOnSecondWeekOfMonth,
            s.servicedOnThirdWeekOfMonth,
            s.servicedOnFourthWeekOfMonth,
            s.servicedOnFifthWeekOfMonth,
            s.servicedOnHolidays,
          )
        )
      }
  }

  // ── Network fetch helpers ──

  private suspend fun fetchAllSweepingData(): List<StreetCleaningResponse> {
    val all = mutableListOf<StreetCleaningResponse>()
    var o = 0
    while (true) {
      val b =
        try {
          api.getStreetCleaningData(API_BATCH_LIMIT, o)
        } catch (e: kotlinx.io.IOException) {
          log.w(e) { "Failed to fetch sweeping data at offset $o" }
          break
        }
      if (b.isEmpty()) break
      all.addAll(b.filter { it.cnn.isNotEmpty() && it.geometry != null })
      o += API_BATCH_LIMIT
    }
    return all
  }

  /** Fetches all pages of a paginated API into a single list. Used by parallel fetch. */
  private suspend fun <T> fetchAll(fetch: suspend (Int, Int) -> List<T>): List<T> {
    val all = mutableListOf<T>()
    var o = 0
    while (true) {
      val b =
        try {
          fetch(API_BATCH_LIMIT, o)
        } catch (e: kotlinx.io.IOException) {
          log.w(e) { "Batch fetch failed at offset $o" }
          break
        }
      if (b.isEmpty()) break
      all.addAll(b)
      o += API_BATCH_LIMIT
    }
    return all
  }

  /**
   * Builds a LineString geometry from meter coordinates.
   *
   * When a CNN has no sweeping centerline, we need a visible polyline for the map. Uses the two
   * most distant meter points to form a line that follows the actual street direction, rather than
   * a bounding-box diagonal.
   */
  private fun buildGeometryFromMeters(
    meters: List<dev.bongballe.parkbuddy.data.sf.model.ParkingMeterResponse>
  ): Geometry {
    data class Point(val lat: Double, val lng: Double)

    val points =
      meters.mapNotNull { m ->
        val lat = m.latitude?.toDoubleOrNull() ?: return@mapNotNull null
        val lng = m.longitude?.toDoubleOrNull() ?: return@mapNotNull null
        if (lat > 0 && lng < 0) Point(lat, lng) else null
      }

    if (points.size < 2) {
      val p = points.firstOrNull() ?: Point(0.0, 0.0)
      return Geometry(
        "LineString",
        listOf(listOf(p.lng, p.lat), listOf(p.lng + 0.0001, p.lat + 0.0001)),
      )
    }

    // Find the two most distant points (these define the street line direction)
    var maxDist = 0.0
    var bestA = points[0]
    var bestB = points[1]
    for (i in points.indices) {
      for (j in i + 1 until points.size) {
        val dLat = points[i].lat - points[j].lat
        val dLng = points[i].lng - points[j].lng
        val dist = dLat * dLat + dLng * dLng
        if (dist > maxDist) {
          maxDist = dist
          bestA = points[i]
          bestB = points[j]
        }
      }
    }

    return Geometry(
      "LineString",
      listOf(listOf(bestA.lng, bestA.lat), listOf(bestB.lng, bestB.lat)),
    )
  }

  // ── Geometry parsing ──

  private fun parseGeometry(s: JsonElement?): Geometry? {
    if (s == null) return null
    val o = s.jsonObject
    val t = o["type"]?.jsonPrimitive?.content ?: return null
    val ca = o["coordinates"]?.jsonArray ?: return null
    val coords =
      when (t) {
        "MultiLineString" ->
          ca.flatMap { l ->
            l.jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.content.toDouble() } }
          }

        "LineString" -> ca.map { p -> p.jsonArray.map { it.jsonPrimitive.content.toDouble() } }
        else -> return null
      }
    return Geometry("LineString", coords)
  }

  // ── Domain model mapping ──

  /**
   * Maps a [PopulatedParkingSpot] (Room join result) to the [ParkingSpot] domain model.
   *
   * Sweeping schedules are sorted by (weekday ordinal, fromHour). The timeline comes directly from
   * the entity (pre-resolved during sync).
   */
  private fun PopulatedParkingSpot.toDomainModel(): ParkingSpot =
    ParkingSpot(
      objectId = spot.objectId,
      geometry = spot.geometry,
      streetName = spot.streetName,
      blockLimits = spot.blockLimits,
      neighborhood = spot.neighborhood,
      rppAreas = spot.rppAreas,
      sweepingCnn = spot.sweepingCnn,
      sweepingSide = spot.sweepingSide,
      sweepingSchedules =
        schedules
          .map {
            SweepingSchedule(
              it.weekday,
              it.fromHour,
              it.toHour,
              it.week1,
              it.week2,
              it.week3,
              it.week4,
              it.week5,
              it.holidays,
            )
          }
          .sortedWith(compareBy({ it.weekday.ordinal }, { it.fromHour })),
      timeline = spot.timeline,
    )
}
