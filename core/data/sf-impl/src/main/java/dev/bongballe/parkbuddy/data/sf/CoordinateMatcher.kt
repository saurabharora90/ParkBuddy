package dev.bongballe.parkbuddy.data.sf

import androidx.annotation.VisibleForTesting
import dev.bongballe.parkbuddy.data.sf.model.StreetCleaningResponse
import dev.bongballe.parkbuddy.model.Geometry
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SweepingMatch(
  val cnn: String,
  val side: String, // "L" or "R"
  val schedules: List<StreetCleaningResponse>,
)

private data class ParsedSweeping(
  val response: StreetCleaningResponse,
  val geometry: Geometry,
  val center: Pair<Double, Double>,
)

class CoordinateMatcher(sweepingData: List<StreetCleaningResponse>) {
  // Pre-parse all geometries once during construction
  private val parsedSweepingData: List<ParsedSweeping>

  // Group schedules by CNN and side for quick lookup
  // Key is "cnn:side" (e.g., "4157000:L"), value is list of schedules
  private val schedulesByCnnAndSide: Map<String, List<StreetCleaningResponse>>

  // Spatial index: grid cells of ~100m × ~100m
  // Key is "lat_cell,lng_cell", value is list of indices into parsedSweepingData
  private val spatialIndex: Map<String, List<Int>>

  init {
    // Parse all geometries upfront (O(m) once, not O(n*m))
    parsedSweepingData =
      sweepingData.mapNotNull { sweeping ->
        val geometry = parseGeometry(sweeping.geometry) ?: return@mapNotNull null
        val center = geometry.center() ?: return@mapNotNull null
        ParsedSweeping(sweeping, geometry, center)
      }

    // Group all schedules by CNN and side
    schedulesByCnnAndSide =
      sweepingData
        .filter { it.cnn.isNotEmpty() && it.cnnRightLeft.isNotEmpty() }
        .groupBy { "${it.cnn}:${it.cnnRightLeft}" }

    // Build spatial index
    // ~0.001 degrees ≈ 111m at equator, less at SF latitude (~85m)
    val cellSize = 0.001
    val index = mutableMapOf<String, MutableList<Int>>()
    parsedSweepingData.forEachIndexed { idx, parsed ->
      val cellKey = getCellKey(parsed.center.first, parsed.center.second, cellSize)
      index.getOrPut(cellKey) { mutableListOf() }.add(idx)
    }
    spatialIndex = index
  }

  fun findMatch(parkingGeometry: Geometry, thresholdMeters: Double = 50.0): SweepingMatch? {
    val parkingCenter = parkingGeometry.center() ?: return null

    // Look in the cell containing the point and all 8 neighbors
    val cellSize = 0.001
    val candidates = getCandidateIndices(parkingCenter.first, parkingCenter.second, cellSize)

    var bestMatch: ParsedSweeping? = null
    var bestDistance = thresholdMeters

    for (idx in candidates) {
      val parsed = parsedSweepingData[idx]
      val distance = distanceToPolyline(parkingCenter, parsed.geometry)
      if (distance < bestDistance) {
        bestDistance = distance
        bestMatch = parsed
      }
    }

    if (bestMatch == null) return null

    val cnn = bestMatch.response.cnn
    val matchedSide = bestMatch.response.cnnRightLeft

    // Determine which side the parking spot is on relative to the sweeping line
    val parkingSide = determineSide(parkingCenter, bestMatch.geometry)

    // If the matched sweeping record's side matches the parking side, use it
    // Otherwise, flip to the other side
    val targetSide =
      if (sidesMatch(matchedSide, parkingSide)) matchedSide else flipSide(matchedSide)

    // Get all schedules for this CNN and side
    val schedules = schedulesByCnnAndSide["$cnn:$targetSide"] ?: listOf(bestMatch.response)

    return SweepingMatch(cnn = cnn, side = targetSide, schedules = schedules)
  }

  // Determine which side of the line the point is on using cross product
  // Returns "L" for left or "R" for right
  @VisibleForTesting
  internal fun determineSide(point: Pair<Double, Double>, lineGeometry: Geometry): String {
    val coords = lineGeometry.coordinates
    if (coords.size < 2) return "R" // Default to right if we can't determine

    // Use first two points to determine line direction
    val p1 = coords.first()
    val p2 = coords.last()
    if (p1.size < 2 || p2.size < 2) return "R"

    // Cross product to determine which side
    // (p2 - p1) × (point - p1)
    val dx = p2[0] - p1[0] // lng difference
    val dy = p2[1] - p1[1] // lat difference
    val px = point.second - p1[0] // point lng - p1 lng
    val py = point.first - p1[1] // point lat - p1 lat

    val cross = dx * py - dy * px
    return if (cross > 0) "L" else "R"
  }

  private fun sidesMatch(sweepingSide: String, computedSide: String): Boolean {
    return sweepingSide.uppercase() == computedSide.uppercase()
  }

  private fun flipSide(side: String): String {
    return if (side.uppercase() == "L") "R" else "L"
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

    // Check 3x3 grid of cells (current + 8 neighbors)
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

  companion object {
    private const val EARTH_RADIUS_METERS = 6371000.0

    fun Geometry.center(): Pair<Double, Double>? {
      if (coordinates.isEmpty()) return null
      val lats = coordinates.mapNotNull { it.getOrNull(1) }
      val lngs = coordinates.mapNotNull { it.getOrNull(0) }
      if (lats.isEmpty() || lngs.isEmpty()) return null
      return lats.average() to lngs.average()
    }

    fun distanceToPolyline(point: Pair<Double, Double>, geometry: Geometry): Double {
      val (lat, lng) = point
      val points = geometry.coordinates
      if (points.isEmpty()) return Double.MAX_VALUE

      var minDistance = Double.MAX_VALUE
      for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        if (p1.size < 2 || p2.size < 2) continue
        val dist = distanceToSegment(lat, lng, p1[1], p1[0], p2[1], p2[0])
        if (dist < minDistance) {
          minDistance = dist
        }
      }
      return minDistance
    }

    fun distanceToSegment(
      lat: Double,
      lng: Double,
      lat1: Double,
      lng1: Double,
      lat2: Double,
      lng2: Double,
    ): Double {
      val rLat1 = Math.toRadians(lat1)
      val rLat2 = Math.toRadians(lat2)
      val rLat = Math.toRadians(lat)

      val dx = (Math.toRadians(lng2) - Math.toRadians(lng1)) * cos((rLat1 + rLat2) / 2)
      val dy = Math.toRadians(lat2) - rLat1
      val x = (Math.toRadians(lng) - Math.toRadians(lng1)) * cos((rLat1 + rLat) / 2)
      val y = Math.toRadians(lat) - rLat1

      val dot = x * dx + y * dy
      val lenSq = dx * dx + dy * dy
      val param = if (lenSq != 0.0) dot / lenSq else -1.0

      val xx: Double
      val yy: Double
      when {
        param < 0 -> {
          xx = 0.0
          yy = 0.0
        }

        param > 1 -> {
          xx = dx
          yy = dy
        }

        else -> {
          xx = dx * param
          yy = dy * param
        }
      }

      val diffX = x - xx
      val diffY = y - yy
      return sqrt(diffX * diffX + diffY * diffY) * EARTH_RADIUS_METERS
    }
  }
}
