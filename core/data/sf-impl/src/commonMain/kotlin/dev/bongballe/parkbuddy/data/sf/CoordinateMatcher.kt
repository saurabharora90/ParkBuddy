package dev.bongballe.parkbuddy.data.sf

import dev.bongballe.parkbuddy.data.repository.utils.LocationUtils
import dev.bongballe.parkbuddy.data.sf.model.StreetCleaningResponse
import dev.bongballe.parkbuddy.data.sf.model.toStreetSide
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.StreetSide
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SweepingMatch(
  val cnn: String,
  val side: StreetSide,
  val geometry: Geometry,
  val schedules: List<StreetCleaningResponse>,
)

private data class ParsedSweeping(
  val cnn: String,
  val geometry: Geometry,
  val center: Pair<Double, Double>,
)

/** CoordinateMatcher handles high-precision spatial matching between street data layers. */
class CoordinateMatcher(sweepingData: List<StreetCleaningResponse>) {
  private val parsedSweepingData: List<ParsedSweeping>
  private val schedulesByCnnAndSide: Map<String, List<StreetCleaningResponse>>
  private val spatialIndex: Map<String, List<Int>>

  init {
    // 1. Group schedules by CNN and Side
    schedulesByCnnAndSide =
      sweepingData
        .filter { it.cnn.isNotEmpty() && it.cnnRightLeft.isNotEmpty() }
        .groupBy { "${it.cnn}:${it.cnnRightLeft}" }

    // 2. Extract unique geometries per CNN
    // We only need one geometry per CNN for matching.
    val uniqueGeometries = mutableMapOf<String, ParsedSweeping>()
    for (sweeping in sweepingData) {
      if (sweeping.cnn.isEmpty() || uniqueGeometries.containsKey(sweeping.cnn)) continue
      val geometry = parseGeometry(sweeping.geometry) ?: continue
      val center = geometry.center() ?: continue
      uniqueGeometries[sweeping.cnn] = ParsedSweeping(sweeping.cnn, geometry, center)
    }
    parsedSweepingData = uniqueGeometries.values.toList()

    // 3. Build spatial index using unique geometries
    val cellSize = 0.001
    val index = mutableMapOf<String, MutableList<Int>>()
    parsedSweepingData.forEachIndexed { idx, parsed ->
      val cellKey = getCellKey(parsed.center.first, parsed.center.second, cellSize)
      index.getOrPut(cellKey) { mutableListOf() }.add(idx)
    }
    spatialIndex = index
  }

  /**
   * Matches a high-resolution polyline (Regulation) to all overlapping street segments. Uses
   * multi-point sampling to ensure long polylines match multiple blocks.
   */
  fun matchPolyline(poly: Geometry, thresholdMeters: Double = 20.0): List<SweepingMatch> {
    val coords = poly.coordinates
    if (coords.isEmpty()) return emptyList()

    // Increased sampling to ensure full coverage (up to 10 points)
    val step = (coords.size / 10).coerceAtLeast(1)
    val sampleIndices = (0 until coords.size step step).toMutableList()
    if (!sampleIndices.contains(coords.size - 1)) sampleIndices.add(coords.size - 1)

    val matches = mutableSetOf<Pair<String, StreetSide>>()

    for (idx in sampleIndices) {
      val point = coords[idx]
      val lat = point[1]
      val lng = point[0]

      val candidates = getCandidateIndices(lat, lng, 0.001)
      var bestMatch: Pair<String, StreetSide>? = null
      var bestDist = thresholdMeters

      for (cIdx in candidates) {
        val parsed = parsedSweepingData[cIdx]

        // Match against CENTERLINE first (most stable)
        val distToCenter = LocationUtils.calculateDistanceToPolyline(lat, lng, parsed.geometry)

        if (distToCenter < bestDist) {
          bestDist = distToCenter
          // Determine side relative to centerline
          val side = LocationUtils.determineSide(lat, lng, parsed.geometry)
          bestMatch = parsed.cnn to side
        }
      }

      bestMatch?.let { matches.add(it) }
    }

    return matches.mapNotNull { (cnn, side) ->
      val sideStr = if (side == StreetSide.LEFT) "L" else "R"
      val schedules = schedulesByCnnAndSide["$cnn:$sideStr"]
      val parsed = parsedSweepingData.firstOrNull { it.cnn == cnn }
      if (parsed != null && schedules != null) {
        SweepingMatch(cnn, side, parsed.geometry, schedules)
      } else null
    }
  }

  fun findMatch(parkingGeometry: Geometry, thresholdMeters: Double = 50.0): SweepingMatch? {
    val parkingCenter = parkingGeometry.center() ?: return null
    val candidates = getCandidateIndices(parkingCenter.first, parkingCenter.second, 0.001)

    var bestMatch: ParsedSweeping? = null
    var bestSide = StreetSide.RIGHT
    var bestDistance = thresholdMeters

    for (idx in candidates) {
      val parsed = parsedSweepingData[idx]
      val dist =
        LocationUtils.calculateDistanceToPolyline(
          parkingCenter.first,
          parkingCenter.second,
          parsed.geometry,
        )

      if (dist < bestDistance) {
        bestDistance = dist
        bestMatch = parsed
        bestSide =
          LocationUtils.determineSide(parkingCenter.first, parkingCenter.second, parsed.geometry)
      }
    }

    if (bestMatch == null) return null

    val cnn = bestMatch.cnn
    val sideStr = if (bestSide == StreetSide.LEFT) "L" else "R"
    val schedules = schedulesByCnnAndSide["$cnn:$sideStr"] ?: emptyList()

    return SweepingMatch(
      cnn = cnn,
      side = bestSide,
      geometry = bestMatch.geometry,
      schedules = schedules,
    )
  }

  /** Finds all available sweeping segments (Left and Right sides) for a given CNN. */
  fun findAllMatchesForCnn(cnn: String): List<SweepingMatch> {
    val results = mutableListOf<SweepingMatch>()
    val parsed = parsedSweepingData.firstOrNull { it.cnn == cnn } ?: return emptyList()

    listOf("L", "R").forEach { sideStr ->
      schedulesByCnnAndSide["$cnn:$sideStr"]?.let { schedules ->
        results.add(
          SweepingMatch(
            cnn = cnn,
            side = sideStr.toStreetSide(),
            geometry = parsed.geometry,
            schedules = schedules,
          )
        )
      }
    }
    return results
  }

  private fun getCellKey(lat: Double, lng: Double, cellSize: Double): String {
    val latCell = floor(lat / cellSize).toInt()
    val lngCell = floor(lng / cellSize).toInt()
    return "$latCell,$lngCell"
  }

  private fun getCandidateIndices(lat: Double, lng: Double, cellSize: Double): List<Int> {
    val latCell = floor(lat / cellSize).toInt()
    val lngCell = floor(lng / cellSize).toInt()
    val result = mutableListOf<Int>()
    for (dLat in -1..1) {
      for (dLng in -1..1) {
        val key = "${latCell + dLat},${lngCell + dLng}"
        spatialIndex[key]?.let { result.addAll(it) }
      }
    }
    return result
  }

  private fun parseGeometry(geometryJson: JsonElement?): Geometry? {
    if (geometryJson == null) return null
    val obj = geometryJson.jsonObject
    val type = obj["type"]?.jsonPrimitive?.content ?: return null
    val coordsArray = obj["coordinates"]?.jsonArray ?: return null
    val coordinates =
      when (type) {
        "MultiLineString" ->
          coordsArray.flatMap { lineArray ->
            lineArray.jsonArray.map { point ->
              point.jsonArray.map { it.jsonPrimitive.content.toDouble() }
            }
          }

        "LineString" ->
          coordsArray.map { point -> point.jsonArray.map { it.jsonPrimitive.content.toDouble() } }

        else -> return null
      }
    return Geometry(type = "LineString", coordinates = coordinates)
  }

  companion object {
    private fun toRadians(degrees: Double): Double = degrees * PI / 180.0

    fun offsetGeometry(geometry: Geometry, side: StreetSide, offsetMeters: Double = 5.0): Geometry {
      val coords = geometry.coordinates
      if (coords.size < 2) return geometry
      val latFactor = 111111.0
      val lngFactor = 111111.0 * cos(toRadians(37.7749))
      val offset = if (side == StreetSide.LEFT) offsetMeters else -offsetMeters
      val newCoords = mutableListOf<List<Double>>()
      for (i in coords.indices) {
        val p = coords[i]
        val prev = if (i > 0) coords[i - 1] else null
        val next = if (i < coords.size - 1) coords[i + 1] else null
        val ref1 = prev ?: p
        val ref2 = next ?: p
        if (ref1 == ref2) {
          newCoords.add(p)
          continue
        }
        val dx = (ref2[0] - ref1[0]) * lngFactor
        val dy = (ref2[1] - ref1[1]) * latFactor
        val len = sqrt(dx * dx + dy * dy)
        if (len < 0.001) {
          newCoords.add(p)
          continue
        }
        val nx = -dy / len
        val ny = dx / len
        val newLng = p[0] + (nx * offset) / lngFactor
        val newLat = p[1] + (ny * offset) / latFactor
        newCoords.add(listOf(newLng, newLat))
      }
      return Geometry(type = "LineString", coordinates = newCoords)
    }

    fun Geometry.center(): Pair<Double, Double>? {
      if (coordinates.isEmpty()) return null
      val lats = coordinates.mapNotNull { it.getOrNull(1) }
      val lngs = coordinates.mapNotNull { it.getOrNull(0) }
      if (lats.isEmpty() || lngs.isEmpty()) return null
      return lats.average() to lngs.average()
    }
  }
}
