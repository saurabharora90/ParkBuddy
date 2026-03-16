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
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.SweepingSchedule
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

        // ── Step 2: Match parking regulations to segments via geometry ──
        // Each regulation polyline is sampled at up to 10 points and matched to the nearest
        // sweeping centerline within MATCHING_THRESHOLD_METERS.
        fetchInBatches(
          fetch = { l, o -> api.getParkingRegulations(l, o) },
          process = { batch ->
            for (reg in batch) {
              val poly = parseGeometry(reg.shape) ?: continue
              val overlaps = matcher.matchPolyline(poly, MATCHING_THRESHOLD_METERS)

              // If no sweeping centerline matches, create a "virtual" segment.
              // Identity Inheritance: find the closest known street for naming.
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

                // Collect RPP areas, filtering out "0" (data quality issue in 2 records)
                reg.rppArea1?.takeIf { it.isNotBlank() && it != "0" }?.let { ctx.rppAreas.add(it) }
                reg.rppArea2?.takeIf { it.isNotBlank() && it != "0" }?.let { ctx.rppAreas.add(it) }
                reg.rppArea3?.takeIf { it.isNotBlank() && it != "0" }?.let { ctx.rppAreas.add(it) }
                reg.exceptions?.let { e ->
                  Regex("Except Area ([A-Z])", RegexOption.IGNORE_CASE).findAll(e).forEach {
                    ctx.rppAreas.add(it.groupValues[1].uppercase())
                  }
                }
                ctx.neighborhood = reg.neighborhood ?: ctx.neighborhood

                // Convert regulation to a candidate ParkingInterval for the resolver.
                // Uses DayParser (fixes "M, TH", "Sa" bugs) and TimeParser.
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
                    exemptPermitZones = ctx.rppAreas.toList(),
                    source = IntervalSource.REGULATION,
                  )
                )
              }
            }
          },
        )

        // ── Step 3: Fetch meter schedules indexed by post_id ──
        // Skips unevaluable event-based schedules (Giants Day, Posted Events, etc.)
        val meterSchedulesByPostId = mutableMapOf<String, MutableList<MeterScheduleResponse>>()
        fetchInBatches(
          fetch = { l, o -> api.getMeterSchedules(l, o) },
          process = { batch ->
            for (s in batch) {
              if (DayParser.parseMeterDays(s.daysApplied) == null) continue
              s.postId?.let { pid ->
                meterSchedulesByPostId.getOrPut(pid) { mutableListOf() }.add(s)
              }
            }
          },
        )

        // ── Step 4: Match meter inventory to segments by CNN ──
        // Filters out "L" (legislated for future install) meters that don't physically exist yet.
        // Keeps "T" (temporarily inactive) since they'll likely be back online soon.
        fetchInBatches(
          fetch = { l, o -> api.getParkingMeterInventory(l, o) },
          process = { batch ->
            val filtered =
              batch.filter { !it.streetSegCtrlnId.isNullOrBlank() && it.activeMeterFlag != "L" }
            val byCnn = filtered.groupBy { checkNotNull(it.streetSegCtrlnId) }
            for ((cnn, meters) in byCnn) {
              val overlaps = matcher.findAllMatchesForCnn(cnn)
              val contexts =
                if (overlaps.isEmpty()) {
                  val f = meters.first()
                  val lat = f.latitude?.toDoubleOrNull() ?: 0.0
                  val lng = f.longitude?.toDoubleOrNull() ?: 0.0
                  val vCnn = "meter_$cnn"
                  listOf(
                    unifiedContexts.getOrPut(vCnn to StreetSide.RIGHT) {
                      UnifiedSegmentContext(vCnn, StreetSide.RIGHT).apply {
                        curbsideGeometry =
                          Geometry(
                            "LineString",
                            listOf(listOf(lng, lat), listOf(lng + 0.00001, lat + 0.00001)),
                          )
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
                  .forEach { pid ->
                    meterSchedulesByPostId[pid]?.let { ctx.meterSchedules.addAll(it) }
                  }
              }
            }
          },
        )

        // ── Step 5: Resolve each segment's timeline and build entities ──
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
        val limitMinutes = r.timeLimit?.filter { it.isDigit() }?.toIntOrNull() ?: 0
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
          m.isTow -> IntervalType.Forbidden("Tow Away Zone")
          m.isCommercial -> IntervalType.Restricted("Commercial Vehicles Only")
          else -> IntervalType.Metered(m.limitMinutes)
        }
      ParkingInterval(
        type = type,
        days = m.days,
        startTime = m.start,
        endTime = m.end,
        exemptPermitZones = rppAreas,
        source = if (m.isTow) IntervalSource.TOW else IntervalSource.METER,
      )
    }
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
        else IntervalType.Restricted("Residential Permit Required")
      ParkingRegulation.METERED -> IntervalType.Metered(limitMinutes)
      ParkingRegulation.COMMERCIAL_ONLY -> IntervalType.Restricted("Commercial Vehicles Only")
      ParkingRegulation.LOADING_ZONE -> IntervalType.Restricted("Loading Zone")
      ParkingRegulation.NO_PARKING -> IntervalType.Forbidden("No Parking")
      ParkingRegulation.NO_STOPPING -> IntervalType.Forbidden("No Stopping")
      ParkingRegulation.NO_OVERNIGHT -> IntervalType.Forbidden("No Overnight Parking")
      ParkingRegulation.GOVERNMENT_ONLY -> null // Doesn't affect regular passenger vehicles
      ParkingRegulation.NO_OVERSIZED -> null // Doesn't affect regular passenger vehicles
      ParkingRegulation.UNKNOWN -> null
    }

  // ── Sweeping schedule entity creation ──

  /**
   * Creates [SweepingScheduleEntity]s from raw sweeping responses.
   *
   * Deduplicates by all fields to handle the (common) case where the API returns duplicate records
   * for the same sweeping window.
   */
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

  private suspend fun <T> fetchInBatches(
    fetch: suspend (Int, Int) -> List<T>,
    process: suspend (List<T>) -> Unit,
  ) {
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
      process(b)
      o += API_BATCH_LIMIT
    }
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
