package dev.bongballe.parkbuddy.data.sf.repository

import co.touchlab.kermit.Logger
import dev.bongballe.parkbuddy.DispatcherType
import dev.bongballe.parkbuddy.data.io.DataFileReader
import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.data.repository.utils.TimelineResolver
import dev.bongballe.parkbuddy.data.sf.BlockfaceRateParser
import dev.bongballe.parkbuddy.data.sf.CoordinateMatcher
import dev.bongballe.parkbuddy.data.sf.DayParser
import dev.bongballe.parkbuddy.data.sf.SegmentGeometry
import dev.bongballe.parkbuddy.data.sf.TimeParser
import dev.bongballe.parkbuddy.data.sf.database.ParkingDao
import dev.bongballe.parkbuddy.data.sf.database.entity.ParkingSpotEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.SweepingScheduleEntity
import dev.bongballe.parkbuddy.data.sf.database.entity.UserPreferencesEntity
import dev.bongballe.parkbuddy.data.sf.database.model.PopulatedParkingSpot
import dev.bongballe.parkbuddy.data.sf.io.SfDataFiles
import dev.bongballe.parkbuddy.data.sf.model.MeterPolicyResponse
import dev.bongballe.parkbuddy.data.sf.model.MeterScheduleResponse
import dev.bongballe.parkbuddy.data.sf.model.ParkingRegulation
import dev.bongballe.parkbuddy.data.sf.model.StreetCenterlineResponse
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisBlockfaceAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisBlockfaceRateAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisCleaningAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisFeature
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisFeatureResponse
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisMeterAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.ArcGisRegulationAttrs
import dev.bongballe.parkbuddy.data.sf.model.arcgis.toLineGeometry
import dev.bongballe.parkbuddy.data.sf.model.toParkingRegulation
import dev.bongballe.parkbuddy.data.sf.model.toStreetSide
import dev.bongballe.parkbuddy.data.sf.network.SfOpenDataApi
import dev.bongballe.parkbuddy.data.sf.network.SfmtaArcGisApi
import dev.bongballe.parkbuddy.data.sf.network.fetchAllArcGis
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.IntervalSource
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ProhibitionReason
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.bongballe.parkbuddy.model.Weekday
import dev.bongballe.parkbuddy.qualifier.WithDispatcherType
import dev.bongballe.parkbuddy.util.LocationUtils
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource

@OptIn(ExperimentalSerializationApi::class)
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ParkingRepositoryImpl(
  private val dao: ParkingDao,
  private val arcGis: SfmtaArcGisApi,
  private val socrata: SfOpenDataApi,
  private val fileReader: DataFileReader,
  private val json: Json,
  @WithDispatcherType(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : ParkingRepository {

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
    val candidateIntervals: MutableList<ParkingInterval> = mutableListOf(),
    val sweepingSchedules: MutableList<ArcGisFeature<ArcGisCleaningAttrs>> = mutableListOf(),
    val meterPolicies: MutableList<MeterPolicyResponse> = mutableListOf(),
    val meterSchedules: MutableList<MeterScheduleResponse> = mutableListOf(),
  )

  companion object {
    private val log = Logger.withTag("ParkingRepository")
    private const val MATCHING_THRESHOLD_METERS = 20.0
    private const val API_BATCH_LIMIT = 5000
    private val RPP_EXCEPTION_REGEX = Regex("Except Area ([A-Z])", RegexOption.IGNORE_CASE)
  }

  override suspend fun hasSpots(): Boolean = dao.getSpotCount() > 0

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

  private data class ProcessingResult(
    val spots: List<ParkingSpotEntity>,
    val sweepingSchedules: List<SweepingScheduleEntity>,
    val hasParkingData: Boolean,
  )

  // ── Public API ──

  override suspend fun populateDb(): Boolean = coroutineScope {
    withContext(ioDispatcher) {
      log.d { "populateDb: reading local data files" }

      val centerlinesD = async {
        readSocrataFile<StreetCenterlineResponse>(SfDataFiles.CENTERLINES)
      }
      val cleaningD = async { readArcGisFile<ArcGisCleaningAttrs>(SfDataFiles.CLEANING) }
      val blockfacesD = async { readArcGisFile<ArcGisBlockfaceAttrs>(SfDataFiles.BLOCKFACES) }
      val metersD = async { readArcGisFile<ArcGisMeterAttrs>(SfDataFiles.METERS) }
      val regsTimedD = async {
        readArcGisFile<ArcGisRegulationAttrs>(SfDataFiles.REGULATIONS_TIMED)
      }
      val regsOtherD = async {
        readArcGisFile<ArcGisRegulationAttrs>(SfDataFiles.REGULATIONS_OTHER)
      }
      val ratesD = async { readArcGisFile<ArcGisBlockfaceRateAttrs>(SfDataFiles.BLOCKFACE_RATES) }
      val policiesD = async { readSocrataFile<MeterPolicyResponse>(SfDataFiles.METER_POLICIES) }
      val schedulesD = async { readSocrataFile<MeterScheduleResponse>(SfDataFiles.METER_SCHEDULES) }

      val centerlines = centerlinesD.await()
      val cleaning = cleaningD.await()
      val blockfaces = blockfacesD.await()
      val meters = metersD.await()
      val regsTimed = regsTimedD.await()
      val regsOther = regsOtherD.await()
      val rates = ratesD.await()
      val policies = policiesD.await()
      val schedules = schedulesD.await()
      log.d {
        "populateDb: centerlines=${centerlines.size}, cleaning=${cleaning.size}, " +
          "blockfaces=${blockfaces.size}, meters=${meters.size}, " +
          "regsTimed=${regsTimed.size}, regsOther=${regsOther.size}, " +
          "rates=${rates.size}, policies=${policies.size}, schedules=${schedules.size}"
      }

      buildDbFromData(
        centerlines,
        cleaning,
        blockfaces,
        meters,
        regsTimed,
        regsOther,
        rates,
        policies,
        schedules,
      )
    }
  }

  override suspend fun refreshData(): Boolean = coroutineScope {
    log.d { "refreshData: downloading from network" }

    // Download all sources in parallel and write each to disk as it completes.
    val centerlinesDeferred = async {
      downloadAndSaveSocrata(SfDataFiles.CENTERLINES) { l, o -> socrata.getStreetCenterlines(l, o) }
    }
    val cleaningDeferred = async {
      downloadAndSaveArcGis(SfDataFiles.CLEANING) { o, l -> arcGis.getStreetCleaning(o, l) }
    }
    val blockfacesDeferred = async {
      downloadAndSaveArcGis(SfDataFiles.BLOCKFACES) { o, l -> arcGis.getMeteredBlockfaces(o, l) }
    }
    val metersDeferred = async {
      downloadAndSaveArcGis(SfDataFiles.METERS) { o, l -> arcGis.getMeters(o, l) }
    }
    val regsTimedDeferred = async {
      downloadAndSaveArcGis(SfDataFiles.REGULATIONS_TIMED) { o, l ->
        arcGis.getTimeLimitedRegulations(o, l)
      }
    }
    val regsOtherDeferred = async {
      downloadAndSaveArcGis(SfDataFiles.REGULATIONS_OTHER) { o, l ->
        arcGis.getOtherRegulations(o, l)
      }
    }
    val ratesDeferred = async {
      downloadAndSaveArcGis(SfDataFiles.BLOCKFACE_RATES) { o, l -> arcGis.getBlockfaceRates(o, l) }
    }
    val policiesDeferred = async {
      downloadAndSaveSocrata(SfDataFiles.METER_POLICIES) { l, o -> socrata.getMeterPolicies(l, o) }
    }
    val schedulesDeferred = async {
      downloadAndSaveSocrata(SfDataFiles.METER_SCHEDULES) { l, o ->
        socrata.getMeterSchedules(l, o)
      }
    }
    // Await all downloads
    val centerlinesOk = centerlinesDeferred.await()
    val cleaningOk = cleaningDeferred.await()
    val blockfacesOk = blockfacesDeferred.await()
    val metersOk = metersDeferred.await()
    val regsTimedOk = regsTimedDeferred.await()
    val regsOtherOk = regsOtherDeferred.await()
    val ratesOk = ratesDeferred.await()
    val policiesOk = policiesDeferred.await()
    val schedulesOk = schedulesDeferred.await()

    if (!cleaningOk || !blockfacesOk || !metersOk) {
      log.w { "refreshData: critical download failed, aborting" }
      return@coroutineScope false
    }
    if (!centerlinesOk || !regsTimedOk || !regsOtherOk || !ratesOk || !policiesOk || !schedulesOk) {
      log.w { "refreshData: some downloads failed, proceeding with available data" }
    }

    log.d { "refreshData: downloads complete, building DB from local files" }
    populateDb()
  }

  // ── Local file reading ──

  private suspend inline fun <reified T> readArcGisFile(fileName: String): List<ArcGisFeature<T>> {
    val source = fileReader.read(fileName)
    return try {
      json.decodeFromBufferedSource<ArcGisFeatureResponse<T>>(source).features
    } finally {
      source.close()
    }
  }

  private suspend inline fun <reified T> readSocrataFile(fileName: String): List<T> {
    val source = fileReader.read(fileName)
    return try {
      json.decodeFromBufferedSource<List<T>>(source)
    } finally {
      source.close()
    }
  }

  // ── Network download + save to disk ──

  private suspend inline fun <reified T> downloadAndSaveArcGis(
    fileName: String,
    crossinline fetch: suspend (offset: Int, limit: Int) -> ArcGisFeatureResponse<T>,
  ): Boolean {
    return try {
      val features = fetchAllArcGis { o, l -> fetch(o, l) }
      val response = ArcGisFeatureResponse(features, exceededTransferLimit = false)
      fileReader.write(fileName, json.encodeToString(response))
      log.d { "downloadAndSaveArcGis: $fileName -> ${features.size} features" }
      true
    } catch (e: Exception) {
      log.w(e) { "downloadAndSaveArcGis: failed for $fileName" }
      false
    }
  }

  private suspend inline fun <reified T> downloadAndSaveSocrata(
    fileName: String,
    crossinline fetch: suspend (limit: Int, offset: Int) -> List<T>,
  ): Boolean {
    return try {
      val all = fetchAllSocrata { l, o -> fetch(l, o) }
      fileReader.write(fileName, json.encodeToString(all))
      log.d { "downloadAndSaveSocrata: $fileName -> ${all.size} records" }
      true
    } catch (e: Exception) {
      log.w(e) { "downloadAndSaveSocrata: failed for $fileName" }
      false
    }
  }

  private data class BlockfaceInfo(
    val geometry: Geometry,
    val streetName: String,
    val side: StreetSide?,
    val addrRange: String?,
  )

  private suspend fun buildDbFromData(
    centerlines: List<StreetCenterlineResponse>,
    cleaningFeatures: List<ArcGisFeature<ArcGisCleaningAttrs>>,
    blockfaceFeatures: List<ArcGisFeature<ArcGisBlockfaceAttrs>>,
    meterFeatures: List<ArcGisFeature<ArcGisMeterAttrs>>,
    timeLimitedRegs: List<ArcGisFeature<ArcGisRegulationAttrs>>,
    otherRegs: List<ArcGisFeature<ArcGisRegulationAttrs>>,
    blockfaceRateFeatures: List<ArcGisFeature<ArcGisBlockfaceRateAttrs>>,
    allMeterPolicies: List<MeterPolicyResponse>,
    allMeterSchedules: List<MeterScheduleResponse>,
  ): Boolean {
    val result =
      withContext(ioDispatcher) {
        val unifiedContexts = mutableMapOf<Pair<String, StreetSide>, UnifiedSegmentContext>()

        // ── Build geometry backbone from street centerlines (authoritative) ──
        // Classcode 1 = freeways, 6 = ramps. Exclude from the backbone so regulations
        // and meters near highway overpasses can't spatially match onto freeway CNNs.
        val nonParkableClasscodes = setOf("1", "6")
        val geometrySources = mutableListOf<SegmentGeometry>()
        for (cl in centerlines) {
          if (cl.cnn.isBlank()) continue
          if (cl.classcode in nonParkableClasscodes) continue
          val geom = cl.line ?: continue
          if (geom.coordinates.size < 2) continue
          geometrySources.add(
            SegmentGeometry(
              cnn = cl.cnn,
              geometry = geom,
              streetName = cl.streetname,
              neighborhood = cl.nhood,
            )
          )
        }

        // ── Index sweeping data (scheduling only, geometry comes from centerlines) ──
        val cleaningByCnnSide =
          mutableMapOf<String, MutableList<ArcGisFeature<ArcGisCleaningAttrs>>>()

        for (f in cleaningFeatures) {
          val cnn = f.attributes.cnn ?: continue
          val sideStr = f.attributes.cnnRightLeft ?: continue
          cleaningByCnnSide.getOrPut("$cnn:$sideStr") { mutableListOf() }.add(f)
        }

        // ── Index blockface geometry ──
        val blockfaceIndex = mutableMapOf<Long, BlockfaceInfo>()
        for (f in blockfaceFeatures) {
          val bfId = f.attributes.blockfaceId?.toLong() ?: continue
          val geom = f.geometry?.toLineGeometry() ?: continue
          val from = f.attributes.fromAddrNo?.toInt()
          val to = f.attributes.toAddrNo?.toInt()
          val side =
            when (f.attributes.strSegOrientation?.uppercase()) {
              "L" -> StreetSide.LEFT
              "R" -> StreetSide.RIGHT
              else -> null
            }
          blockfaceIndex[bfId] =
            BlockfaceInfo(
              geom,
              f.attributes.streetName ?: "",
              side,
              if (from != null && to != null) "$from-$to" else null,
            )
        }

        // ── Build meter bridges ──
        val filteredMeters =
          meterFeatures.filter { m ->
            m.attributes.activeMeterFlag != "U" && m.attributes.activeMeterFlag != "L"
          }
        val blockfaceToCnn = mutableMapOf<Long, String>()
        for (m in filteredMeters) {
          val bfId = m.attributes.blockfaceId?.toLong() ?: continue
          val cnn = m.attributes.cnn ?: continue
          blockfaceToCnn.getOrPut(bfId) { cnn }
        }

        val matcher = CoordinateMatcher(geometrySources)

        // ── Seed sweeping contexts from cleaning data ──
        for ((cnnSide, features) in cleaningByCnnSide) {
          val colonIdx = cnnSide.lastIndexOf(':')
          val cnn = cnnSide.substring(0, colonIdx)
          val sideStr = cnnSide.substring(colonIdx + 1)
          val side = sideStr.toStreetSide()
          val ctx = unifiedContexts.getOrPut(cnn to side) { UnifiedSegmentContext(cnn, side) }
          ctx.sweepingSchedules.addAll(features)
          if (ctx.centerline == null) {
            ctx.centerline = matcher.getGeometryByCnn(cnn)
            val first = features.first().attributes
            ctx.streetName = first.corridor ?: first.streetName
          }
          if (ctx.blockLimits == null) {
            val a = features.first().attributes
            val (from, to) =
              if (side == StreetSide.LEFT) a.leftFromAddr to a.leftToAddr
              else a.rightFromAddr to a.rightToAddr
            if (from != null && to != null) ctx.blockLimits = "$from-$to"
          }
        }

        // ── Index meter schedules ──
        val meterPoliciesByPostId = mutableMapOf<String, MutableList<MeterPolicyResponse>>()
        for (p in allMeterPolicies) {
          if (p.postId.isBlank()) continue
          meterPoliciesByPostId.getOrPut(p.postId) { mutableListOf() }.add(p)
        }

        val meterSchedulesByPostId = mutableMapOf<String, MutableList<MeterScheduleResponse>>()
        for (s in allMeterSchedules) {
          val pid = s.postId ?: continue
          if (pid in meterPoliciesByPostId) continue
          if (DayParser.parseMeterDays(s.daysApplied) == null) continue
          meterSchedulesByPostId.getOrPut(pid) { mutableListOf() }.add(s)
        }

        val blockfaceRatesByBfId = mutableMapOf<Long, ArcGisBlockfaceRateAttrs>()
        for (f in blockfaceRateFeatures) {
          val bfId = f.attributes.blockfaceId ?: continue
          blockfaceRatesByBfId[bfId] = f.attributes
        }

        // ── Merge meter data into existing contexts (or create new ones) ──
        //
        // A blockface can span multiple CNN segments. Spatially match each blockface
        // against the CNN backbone to find ALL overlapping CNNs, then merge meter data
        // into each one. This prevents sweeping-only CNN segments from appearing as
        // separate lines when they're physically covered by a metered blockface.
        val metersByBlockface =
          filteredMeters
            .filter { it.attributes.blockfaceId != null }
            .groupBy { it.attributes.blockfaceId!!.toLong() }

        for ((bfId, meters) in metersByBlockface) {
          val bf = blockfaceIndex[bfId] ?: continue
          val bfSide = bf.side ?: StreetSide.RIGHT

          // Fast path: if explicit CNN exists in backbone, skip expensive spatial matching.
          // Spatial matching is only needed when blockfaces span multiple CNN segments.
          val explicitCnn = blockfaceToCnn[bfId]
          val allCnns =
            if (explicitCnn != null && matcher.hasCnn(explicitCnn)) {
              setOf(explicitCnn)
            } else {
              val spatialMatches =
                matcher
                  .matchPolyline(bf.geometry, MATCHING_THRESHOLD_METERS)
                  .filter { it.side == bfSide }
                  .map { it.cnn }
              buildSet {
                if (explicitCnn != null) add(explicitCnn)
                addAll(spatialMatches)
              }
            }

          val targetIds = if (allCnns.isNotEmpty()) allCnns else setOf("bf_$bfId")

          for (segId in targetIds) {
            val ctx =
              unifiedContexts.getOrPut(segId to bfSide) {
                UnifiedSegmentContext(segId, bfSide).apply {
                  streetName = bf.streetName.ifEmpty { meters.first().attributes.streetName }
                  centerline = matcher.getGeometryByCnn(segId)
                }
              }

            if (ctx.curbsideGeometry == null) ctx.curbsideGeometry = bf.geometry
            if (ctx.blockLimits == null) bf.addrRange?.let { ctx.blockLimits = it }
            ctx.regulation = ParkingRegulation.METERED

            if (ctx.sweepingSchedules.isEmpty()) {
              val sideStr = if (bfSide == StreetSide.LEFT) "L" else "R"
              cleaningByCnnSide["$segId:$sideStr"]?.let { ctx.sweepingSchedules.addAll(it) }
            }

            for (m in meters) {
              val pid = m.attributes.postId ?: continue
              meterPoliciesByPostId[pid]?.let { ctx.meterPolicies.addAll(it) }
              if (ctx.meterPolicies.isEmpty()) {
                meterSchedulesByPostId[pid]?.let { ctx.meterSchedules.addAll(it) }
              }
            }

            if (ctx.meterPolicies.isEmpty() && ctx.meterSchedules.isEmpty()) {
              val rateAttrs = blockfaceRatesByBfId[bfId]
              val rateIntervals =
                BlockfaceRateParser.parse(rateAttrs?.rateSched, ctx.rppAreas.toList())
              ctx.candidateIntervals.addAll(rateIntervals)
            }
          }
        }

        // ── Spatial-match regulations against backbone ──
        val allRegulations = timeLimitedRegs + otherRegs
        for (f in allRegulations) {
          val attrs = f.attributes
          val poly = f.geometry?.toLineGeometry() ?: continue
          val regulation = attrs.regulation ?: continue

          val regType = regulation.toParkingRegulation()
          val isForbiddenReg =
            regType == ParkingRegulation.NO_PARKING || regType == ParkingRegulation.NO_STOPPING

          var overlaps = matcher.matchPolyline(poly, MATCHING_THRESHOLD_METERS)

          // Filter partial-block no-parking regulations per CNN. A driveway, hydrant, or bus
          // stop covers a small fraction of a block and should not make the whole block Forbidden.
          if (isForbiddenReg && attrs.lengthFt != null && attrs.lengthFt > 0) {
            val regLength = LocationUtils.polylineLength(poly)
            overlaps =
              overlaps.filter { match ->
                val blockLength = LocationUtils.polylineLength(match.geometry)
                blockLength <= 0 || (regLength / blockLength) >= 0.4
              }
          }

          val contexts =
            if (overlaps.isEmpty()) {
              val vCnn = "reg_${attrs.objectId}"
              val key = vCnn to StreetSide.RIGHT
              listOf(
                unifiedContexts.getOrPut(key) {
                  UnifiedSegmentContext(vCnn, StreetSide.RIGHT).apply {
                    curbsideGeometry = poly
                    val coord = poly.coordinates.firstOrNull()
                    if (coord != null && coord.size >= 2) {
                      matcher.findClosestSegment(coord[1], coord[0])?.let { closest ->
                        this.streetName = closest.streetName
                      } ?: run { this.streetName = "Unknown Street" }
                    } else {
                      this.streetName = "Unknown Street"
                    }
                  }
                }
              )
            } else {
              overlaps.map { match ->
                unifiedContexts.getOrPut(match.cnn to match.side) {
                  UnifiedSegmentContext(match.cnn, match.side).apply { centerline = match.geometry }
                }
              }
            }

          for (ctx in contexts) {
            if (ctx.curbsideGeometry == null) ctx.curbsideGeometry = poly

            val incoming = regulation.toParkingRegulation()
            val current = ctx.regulation
            if (current == null || incoming.rank() > current.rank()) ctx.regulation = incoming

            listOfNotNull(attrs.rppArea1, attrs.rppArea2, attrs.rppArea3)
              .filter { it.isNotBlank() && it != " " && it != "0" }
              .forEach { ctx.rppAreas.add(it.trim()) }
            attrs.exceptions?.let { e ->
              RPP_EXCEPTION_REGEX.findAll(e).forEach {
                ctx.rppAreas.add(it.groupValues[1].uppercase())
              }
            }

            attachSweepingIfMissing(ctx, cleaningByCnnSide)

            val limit = TimeParser.parseTimeLimitHours(attrs.hrLimit)
            val days = DayParser.parseRegulationDays(attrs.days)
            val start =
              TimeParser.parseRegulationTime(attrs.hrsBegin)
                ?: TimeParser.parseSimpleAmPm(attrs.fromTime)
                ?: continue
            val end =
              TimeParser.parseRegulationTime(attrs.hrsEnd)
                ?: TimeParser.parseSimpleAmPm(attrs.toTime)
                ?: continue
            val (normStart, normEnd) = TimeParser.normalizeWindow(start, end)

            val intervalType = regulationToIntervalType(incoming, limit) ?: continue
            ctx.candidateIntervals.add(
              ParkingInterval(
                type = intervalType,
                days = days,
                startTime = normStart,
                endTime = normEnd,
                exemptPermitZones = permitExemptZonesFor(intervalType, ctx.rppAreas.toList()),
                source = IntervalSource.REGULATION,
              )
            )
          }
        }

        // ── Merge sweeping-only contexts into adjacent neighbors ──
        //
        // SFMTA splits blocks into multiple CNN segments. A sweeping-only segment
        // adjacent to a regulated/metered segment on the same street+side is the same
        // physical block. Absorb it into the neighbor to avoid duplicate map lines.
        //
        // Index by (streetName, side) for O(1) candidate lookup instead of linear scan.
        val sweepingOnlyKeys =
          unifiedContexts.entries
            .filter { (_, ctx) ->
              ctx.sweepingSchedules.isNotEmpty() &&
                ctx.regulation == null &&
                ctx.meterPolicies.isEmpty() &&
                ctx.meterSchedules.isEmpty() &&
                ctx.candidateIntervals.isEmpty()
            }
            .map { it.key }
            .toSet()

        val neighborIndex =
          mutableMapOf<Pair<String, StreetSide>, MutableList<Pair<String, StreetSide>>>()
        for ((key, ctx) in unifiedContexts) {
          if (key in sweepingOnlyKeys) continue
          val street = ctx.streetName?.lowercase() ?: continue
          neighborIndex.getOrPut(street to ctx.side) { mutableListOf() }.add(key)
        }

        val absorbed = mutableSetOf<Pair<String, StreetSide>>()
        for (soKey in sweepingOnlyKeys) {
          val soCtx = unifiedContexts[soKey] ?: continue
          val soGeom = soCtx.curbsideGeometry ?: soCtx.centerline ?: continue
          val soStreet = soCtx.streetName?.lowercase() ?: continue

          val candidates = neighborIndex[soStreet to soCtx.side] ?: continue
          val neighbor =
            candidates.firstOrNull { key ->
              key !in absorbed &&
                geometriesAdjacent(
                  soGeom,
                  unifiedContexts[key]?.let { it.curbsideGeometry ?: it.centerline },
                )
            }

          if (neighbor != null) {
            val nCtx = unifiedContexts[neighbor] ?: continue
            nCtx.sweepingSchedules.addAll(soCtx.sweepingSchedules)
            if (nCtx.blockLimits != null && soCtx.blockLimits != null) {
              nCtx.blockLimits = mergeBlockLimits(nCtx.blockLimits!!, soCtx.blockLimits!!)
            } else if (nCtx.blockLimits == null) {
              nCtx.blockLimits = soCtx.blockLimits
            }
            nCtx.rppAreas.addAll(soCtx.rppAreas)
            absorbed.add(soKey)
          }
        }
        for (key in absorbed) unifiedContexts.remove(key)

        // ── Resolve timelines and build entities ──
        val spots = mutableListOf<ParkingSpotEntity>()
        val sweeping = mutableListOf<SweepingScheduleEntity>()

        for ((key, ctx) in unifiedContexts) {
          if (ctx.streetName.isNullOrEmpty()) {
            ctx.streetName = matcher.getStreetNameByCnn(ctx.cnn)
          }
          if (ctx.streetName == "Unknown Street") continue

          val hasAnyData =
            ctx.regulation != null ||
              ctx.meterPolicies.isNotEmpty() ||
              ctx.meterSchedules.isNotEmpty() ||
              ctx.candidateIntervals.isNotEmpty() ||
              ctx.sweepingSchedules.isNotEmpty()
          if (!hasAnyData) continue

          val geom =
            when {
              ctx.centerline != null ->
                CoordinateMatcher.offsetGeometry(checkNotNull(ctx.centerline), ctx.side)
              ctx.curbsideGeometry != null -> ctx.curbsideGeometry
              else -> null
            } ?: continue

          val sId = "cnn_${ctx.cnn}_${ctx.side.name}"

          addSweepingSchedules(sId, ctx.sweepingSchedules, sweeping)

          val meterIntervals =
            when {
              ctx.meterPolicies.isNotEmpty() ->
                parseMeterPoliciesToIntervals(ctx.meterPolicies, ctx.rppAreas.toList())

              ctx.meterSchedules.isNotEmpty() ->
                parseMeterSchedulesToIntervals(ctx.meterSchedules, ctx.rppAreas.toList())

              else -> emptyList()
            }

          // Retroactively apply RPP exemptions to candidate intervals that were created
          // before regulations ran (e.g., BlockfaceRateParser intervals from the meter merge).
          val rppList = ctx.rppAreas.toList()
          val candidatesWithRpp =
            ctx.candidateIntervals.map { interval ->
              if (interval.exemptPermitZones.isEmpty() && rppList.isNotEmpty()) {
                interval.copy(exemptPermitZones = permitExemptZonesFor(interval.type, rppList))
              } else {
                interval
              }
            }
          val allCandidates = candidatesWithRpp + meterIntervals
          val timeline = TimelineResolver.resolve(allCandidates)

          val realCnn =
            if (ctx.cnn.startsWith("bf_")) {
              val bfId = ctx.cnn.removePrefix("bf_").toLongOrNull()
              bfId?.let { blockfaceToCnn[it] } ?: ctx.cnn
            } else {
              ctx.cnn
            }

          spots.add(
            ParkingSpotEntity(
              sId,
              geom,
              ctx.centerline,
              ctx.streetName?.toTitleCase(),
              ctx.blockLimits,
              ctx.neighborhood,
              ctx.rppAreas.toList().sorted(),
              timeline,
              realCnn,
              ctx.side,
            )
          )
        }
        log.d { "buildDbFromData: spots=${spots.size}, sweeping=${sweeping.size}" }
        ProcessingResult(spots, sweeping, spots.isNotEmpty())
      }

    if (result.hasParkingData) {
      dao.replaceAllData(result.spots, result.sweepingSchedules)
      log.d { "buildDbFromData: DB write complete" }
    }
    return result.hasParkingData
  }

  // ── Meter schedule parsing ──

  private data class ParsedMeterEntry(
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val limitMinutes: Int,
    val isTow: Boolean,
    val isCommercial: Boolean,
  )

  private fun meterEntriesToIntervals(
    entries: List<ParsedMeterEntry>,
    rppAreas: List<String>,
  ): List<ParkingInterval> {
    val grouped =
      entries
        .groupBy { listOf(it.start, it.end, it.limitMinutes, it.isTow, it.isCommercial) }
        .map { (_, group) ->
          val mergedDays = group.flatMap { it.days }.toSet()
          group.first().copy(days = mergedDays)
        }

    return grouped.map { m ->
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

  private fun parseMeterPoliciesToIntervals(
    policies: List<MeterPolicyResponse>,
    rppAreas: List<String>,
  ): List<ParkingInterval> {
    val entries =
      policies.mapNotNull { p ->
        val day = DayParser.parseMeterDays(p.dayOfWeek)?.singleOrNull() ?: return@mapNotNull null
        if (p.scheduleType.equals("FREE", true)) return@mapNotNull null
        val rawStart = TimeParser.parsePolicyTime(p.startTime) ?: return@mapNotNull null
        val rawEnd = TimeParser.parsePolicyTime(p.endTime) ?: return@mapNotNull null
        val (start, end) = TimeParser.normalizeWindow(rawStart, rawEnd)
        val rawLimit = p.timeLimitMinutes?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val windowMinutes = windowDurationMinutes(start, end)
        val capLower = p.capColor?.lowercase() ?: ""

        ParsedMeterEntry(
          days = setOf(day),
          start = start,
          end = end,
          limitMinutes = if (rawLimit >= windowMinutes) 0 else rawLimit,
          isTow = p.scheduleType.equals("TOW", true),
          isCommercial = capLower in setOf("yellow", "red", "orange"),
        )
      }
    return meterEntriesToIntervals(entries, rppAreas)
  }

  private fun parseMeterSchedulesToIntervals(
    responses: List<MeterScheduleResponse>,
    rppAreas: List<String>,
  ): List<ParkingInterval> {
    val entries =
      responses.mapNotNull { r ->
        val days = DayParser.parseMeterDays(r.daysApplied) ?: return@mapNotNull null
        val rawStart = TimeParser.parseMeterTime(r.fromTime) ?: return@mapNotNull null
        val rawEnd = TimeParser.parseMeterTime(r.toTime) ?: return@mapNotNull null
        val (start, end) = TimeParser.normalizeWindow(rawStart, rawEnd)
        val rawLimit = r.timeLimit?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val windowMinutes = windowDurationMinutes(start, end)
        val colorPrefix = r.appliedColorRule?.substringBefore("-")?.trim()?.lowercase()

        ParsedMeterEntry(
          days = days,
          start = start,
          end = end,
          limitMinutes = if (rawLimit >= windowMinutes) 0 else rawLimit,
          isTow = r.scheduleType?.contains("Tow", true) == true,
          isCommercial = colorPrefix in setOf("yellow", "red", "orange"),
        )
      }
    return meterEntriesToIntervals(entries, rppAreas)
  }

  private fun windowDurationMinutes(start: LocalTime, end: LocalTime): Int {
    val s = start.hour * 60 + start.minute
    val e = end.hour * 60 + end.minute
    return if (e > s) e - s else (1440 - s + e)
  }

  // ── Regulation mapping ──

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

  private fun regulationToIntervalType(reg: ParkingRegulation, limitMinutes: Int): IntervalType? =
    when (reg) {
      ParkingRegulation.TIME_LIMITED ->
        if (limitMinutes > 0) IntervalType.Limited(limitMinutes) else null

      ParkingRegulation.PAY_OR_PERMIT,
      ParkingRegulation.PAID_PLUS_PERMIT -> IntervalType.Metered(limitMinutes)

      ParkingRegulation.RPP_ONLY ->
        if (limitMinutes > 0) IntervalType.Limited(limitMinutes)
        else IntervalType.Restricted(ProhibitionReason.RESIDENTIAL_PERMIT)

      ParkingRegulation.METERED -> IntervalType.Metered(limitMinutes)
      ParkingRegulation.COMMERCIAL_ONLY -> IntervalType.Restricted(ProhibitionReason.COMMERCIAL)
      ParkingRegulation.LOADING_ZONE -> IntervalType.Restricted(ProhibitionReason.LOADING_ZONE)
      ParkingRegulation.NO_PARKING -> IntervalType.Forbidden(ProhibitionReason.NO_PARKING)
      ParkingRegulation.NO_STOPPING -> IntervalType.Forbidden(ProhibitionReason.TOW_AWAY)
      ParkingRegulation.NO_OVERNIGHT -> IntervalType.Forbidden(ProhibitionReason.NO_OVERNIGHT)
      ParkingRegulation.GOVERNMENT_ONLY -> null
      ParkingRegulation.NO_OVERSIZED -> null
      ParkingRegulation.UNKNOWN -> null
    }

  private fun permitExemptZonesFor(type: IntervalType, rppAreas: List<String>): List<String> =
    when (type) {
      is IntervalType.Limited,
      is IntervalType.Metered -> rppAreas

      is IntervalType.Restricted ->
        if (type.reason == ProhibitionReason.RESIDENTIAL_PERMIT) rppAreas else emptyList()

      is IntervalType.Forbidden,
      is IntervalType.Open -> emptyList()
    }

  // ── Sweeping helpers ──

  private fun attachSweepingIfMissing(
    ctx: UnifiedSegmentContext,
    cleaningByCnnSide: Map<String, List<ArcGisFeature<ArcGisCleaningAttrs>>>,
  ) {
    if (ctx.sweepingSchedules.isNotEmpty()) return
    val sideStr = if (ctx.side == StreetSide.LEFT) "L" else "R"
    cleaningByCnnSide["${ctx.cnn}:$sideStr"]?.let { ctx.sweepingSchedules.addAll(it) }
  }

  private fun addSweepingSchedules(
    sId: String,
    features: List<ArcGisFeature<ArcGisCleaningAttrs>>,
    out: MutableList<SweepingScheduleEntity>,
  ) {
    data class Key(val weekday: Weekday, val fromHour: Int, val toHour: Int)
    data class Flags(
      var week1: Boolean = false,
      var week2: Boolean = false,
      var week3: Boolean = false,
      var week4: Boolean = false,
      var week5: Boolean = false,
      var holidays: Boolean = false,
    )

    val merged = mutableMapOf<Key, Flags>()
    for (f in features) {
      val a = f.attributes
      val weekday = parseWeekday(a.weekday) ?: continue
      val key = Key(weekday, parseCleaningHour(a.fromHour), parseCleaningHour(a.toHour))
      val flags = merged.getOrPut(key) { Flags() }
      if (a.week1 == "Y") flags.week1 = true
      if (a.week2 == "Y") flags.week2 = true
      if (a.week3 == "Y") flags.week3 = true
      if (a.week4 == "Y") flags.week4 = true
      if (a.week5 == "Y") flags.week5 = true
      if (a.holidays == "Y") flags.holidays = true
    }

    for ((key, flags) in merged) {
      out.add(
        SweepingScheduleEntity(
          sId,
          key.weekday,
          key.fromHour,
          key.toHour,
          flags.week1,
          flags.week2,
          flags.week3,
          flags.week4,
          flags.week5,
          flags.holidays,
        )
      )
    }
  }

  private fun parseWeekday(s: String?): Weekday? =
    when (s?.lowercase()?.take(3)) {
      "mon" -> Weekday.Mon
      "tue" -> Weekday.Tues
      "wed" -> Weekday.Wed
      "thu" -> Weekday.Thu
      "fri" -> Weekday.Fri
      "sat" -> Weekday.Sat
      "sun" -> Weekday.Sun
      "hol" -> Weekday.Holiday
      else -> null
    }

  private fun parseCleaningHour(s: String?): Int = TimeParser.parsePolicyTime(s)?.hour ?: 0

  // ── Network helpers ──

  private suspend fun <T> fetchAllSocrata(fetch: suspend (Int, Int) -> List<T>): List<T> {
    val all = mutableListOf<T>()
    var o = 0
    while (true) {
      val b =
        try {
          fetch(API_BATCH_LIMIT, o)
        } catch (e: kotlinx.io.IOException) {
          log.w(e) { "Socrata fetch failed at offset $o" }
          break
        }
      if (b.isEmpty()) break
      all.addAll(b)
      o += API_BATCH_LIMIT
    }
    return all
  }

  // ── Domain model mapping ──

  private fun PopulatedParkingSpot.toDomainModel(): ParkingSpot =
    ParkingSpot(
      objectId = spot.objectId,
      geometry = spot.geometry,
      centerlineGeometry = spot.centerlineGeometry,
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

private val LOWERCASE_WORDS = setOf("of", "the", "and", "at", "in", "on", "to", "de", "la", "el")
private val ORDINAL_REGEX = Regex("^0*(\\d+)(st|nd|rd|th)$", RegexOption.IGNORE_CASE)

private fun String.toTitleCase(): String =
  lowercase()
    .split(' ')
    .mapIndexed { i, word ->
      val ordinal = ORDINAL_REGEX.matchEntire(word)
      when {
        ordinal != null -> "${ordinal.groupValues[1]}${ordinal.groupValues[2]}"
        i > 0 && word in LOWERCASE_WORDS -> word
        else -> word.replaceFirstChar { it.uppercase() }
      }
    }
    .joinToString(" ")

/**
 * Two geometries are nearby: endpoints within ~30m, OR the midpoint of one falls within ~30m of the
 * other's polyline (handles containment/overlap).
 */
private fun geometriesAdjacent(a: Geometry?, b: Geometry?): Boolean {
  if (a == null || b == null) return false
  val ac = a.coordinates
  val bc = b.coordinates
  if (ac.isEmpty() || bc.isEmpty()) return false

  val t = 0.0003 // ~30m at SF latitude

  // Endpoint adjacency
  val ae = listOf(ac.first(), ac.last())
  val be = listOf(bc.first(), bc.last())
  if (
    ae.any { p ->
      be.any { q ->
        p.size >= 2 &&
          q.size >= 2 &&
          kotlin.math.abs(p[0] - q[0]) < t &&
          kotlin.math.abs(p[1] - q[1]) < t
      }
    }
  )
    return true

  // Midpoint-near-line: one segment contained within or overlapping the other
  val am = ac[ac.size / 2]
  val bm = bc[bc.size / 2]
  return pointNearPolyline(am, bc, t) || pointNearPolyline(bm, ac, t)
}

private fun pointNearPolyline(pt: List<Double>, line: List<List<Double>>, t: Double): Boolean {
  if (pt.size < 2) return false
  val px = pt[0]
  val py = pt[1]
  for (i in 0 until line.size - 1) {
    val ax = line[i][0]
    val ay = line[i][1]
    val bx = line[i + 1][0]
    val by = line[i + 1][1]
    val dx = bx - ax
    val dy = by - ay
    val lenSq = dx * dx + dy * dy
    if (lenSq == 0.0) continue
    val ct = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
    if (kotlin.math.abs(px - (ax + ct * dx)) < t && kotlin.math.abs(py - (ay + ct * dy)) < t)
      return true
  }
  return false
}

/** Merges two "from-to" block limit strings into a combined range. */
private fun mergeBlockLimits(a: String, b: String): String {
  val nums = (a.split("-") + b.split("-")).mapNotNull { it.trim().toIntOrNull() }
  if (nums.isEmpty()) return a
  return "${nums.min()}-${nums.max()}"
}
