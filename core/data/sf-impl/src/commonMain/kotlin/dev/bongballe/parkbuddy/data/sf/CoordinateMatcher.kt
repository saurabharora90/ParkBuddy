package dev.bongballe.parkbuddy.data.sf

import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.util.LocationUtils
import dev.bongballe.parkbuddy.util.center
import dev.bongballe.parkbuddy.util.toRadians
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * A street segment with geometry, identified by CNN.
 *
 * This is the input to [CoordinateMatcher]. Any data source that carries street geometry (sweeping,
 * regulations, meters) can contribute segments.
 */
data class SegmentGeometry(
  val cnn: String,
  val geometry: Geometry,
  val streetName: String,
  val neighborhood: String? = null,
)

/** Result of matching a polyline or point against the spatial index. */
data class SegmentMatch(val cnn: String, val side: StreetSide, val geometry: Geometry)

data class ClosestSegment(val streetName: String, val neighborhood: String?)

private data class IndexedSegment(
  val cnn: String,
  val geometry: Geometry,
  val center: Pair<Double, Double>,
  val streetName: String,
  val neighborhood: String?,
  val minLat: Double,
  val maxLat: Double,
  val minLng: Double,
  val maxLng: Double,
)

/**
 * Spatial index for matching coordinates, polylines, and CNNs against street geometry.
 *
 * Geometry can come from any source (sweeping, regulations, meters). The first geometry registered
 * for a given CNN wins; later duplicates are ignored (sweeping centerlines are the most accurate,
 * so feed them first).
 */
class CoordinateMatcher(segments: List<SegmentGeometry>) {
  private val indexedSegments: List<IndexedSegment>
  private val cnnToIndex: Map<String, Int>
  private val spatialIndex: Map<Long, List<Int>>

  init {
    val unique = mutableMapOf<String, IndexedSegment>()
    for (seg in segments) {
      if (seg.cnn.isEmpty() || unique.containsKey(seg.cnn)) continue
      val center = seg.geometry.center() ?: continue
      val coords = seg.geometry.coordinates
      var minLat = Double.MAX_VALUE
      var maxLat = -Double.MAX_VALUE
      var minLng = Double.MAX_VALUE
      var maxLng = -Double.MAX_VALUE
      for (c in coords) {
        if (c.size < 2) continue
        val lng = c[0]
        val lat = c[1]
        if (lat < minLat) minLat = lat
        if (lat > maxLat) maxLat = lat
        if (lng < minLng) minLng = lng
        if (lng > maxLng) maxLng = lng
      }
      unique[seg.cnn] =
        IndexedSegment(
          cnn = seg.cnn,
          geometry = seg.geometry,
          center = center,
          streetName = seg.streetName,
          neighborhood = seg.neighborhood,
          minLat = minLat,
          maxLat = maxLat,
          minLng = minLng,
          maxLng = maxLng,
        )
    }
    indexedSegments = unique.values.toList()

    val cellSize = 0.001
    val cnnMap = mutableMapOf<String, Int>()
    val cellIndex = mutableMapOf<Long, MutableList<Int>>()
    indexedSegments.forEachIndexed { idx, seg ->
      cnnMap[seg.cnn] = idx

      // Index the entire bounding box of the street segment.
      // This ensures that long streets (which can span hundreds of meters)
      // are discoverable across all grid cells they intersect, rather than
      // disappearing if the search is triggered far from their midpoint.
      val minLatCell = floor(seg.minLat / cellSize).toInt()
      val maxLatCell = floor(seg.maxLat / cellSize).toInt()
      val minLngCell = floor(seg.minLng / cellSize).toInt()
      val maxLngCell = floor(seg.maxLng / cellSize).toInt()
      for (latC in minLatCell..maxLatCell) {
        for (lngC in minLngCell..maxLngCell) {
          val cellKey = latC.toLong().shl(32) or (lngC.toLong() and 0xFFFFFFFFL)
          cellIndex.getOrPut(cellKey) { mutableListOf() }.add(idx)
        }
      }
    }
    cnnToIndex = cnnMap
    spatialIndex = cellIndex
  }

  fun hasCnn(cnn: String): Boolean = cnnToIndex.containsKey(cnn)

  fun getGeometryByCnn(cnn: String): Geometry? =
    cnnToIndex[cnn]?.let { indexedSegments[it].geometry }

  fun getStreetNameByCnn(cnn: String): String? =
    cnnToIndex[cnn]?.let { indexedSegments[it].streetName.ifEmpty { null } }

  fun findClosestSegment(
    lat: Double,
    lng: Double,
    thresholdMeters: Double = 100.0,
  ): ClosestSegment? {
    val candidates = getCandidateIndices(lat, lng, 0.001)
    val margin = thresholdMeters / METERS_PER_DEGREE
    var bestMatch: IndexedSegment? = null
    var bestDist = thresholdMeters

    for (idx in candidates) {
      val seg = indexedSegments[idx]
      if (
        lat < seg.minLat - margin ||
          lat > seg.maxLat + margin ||
          lng < seg.minLng - margin ||
          lng > seg.maxLng + margin
      )
        continue
      val dist = LocationUtils.calculateDistanceToPolyline(lat, lng, seg.geometry)
      if (dist < bestDist) {
        bestDist = dist
        bestMatch = seg
      }
    }

    return bestMatch?.let { ClosestSegment(it.streetName, it.neighborhood) }
  }

  /**
   * Matches a polyline against the spatial index, returning all (CNN, side) pairs within threshold.
   */
  fun matchPolyline(poly: Geometry, thresholdMeters: Double = 20.0): List<SegmentMatch> {
    val coords = poly.coordinates
    if (coords.isEmpty()) return emptyList()

    val samplePoints = buildSamplePoints(coords)

    val margin = thresholdMeters / METERS_PER_DEGREE
    val matches = mutableSetOf<Pair<String, StreetSide>>()

    for ((lat, lng) in samplePoints) {
      val candidates = getCandidateIndices(lat, lng, 0.001)
      var bestMatch: Pair<String, StreetSide>? = null
      var bestDist = thresholdMeters

      for (cIdx in candidates) {
        val seg = indexedSegments[cIdx]
        if (
          lat < seg.minLat - margin ||
            lat > seg.maxLat + margin ||
            lng < seg.minLng - margin ||
            lng > seg.maxLng + margin
        )
          continue
        val distToCenter = LocationUtils.calculateDistanceToPolyline(lat, lng, seg.geometry)

        if (distToCenter < bestDist) {
          bestDist = distToCenter
          val side = LocationUtils.determineSide(lat, lng, seg.geometry)
          bestMatch = seg.cnn to side
        }
      }
      bestMatch?.let { matches.add(it) }
    }

    return matches.map { (cnn, side) ->
      val seg = indexedSegments[checkNotNull(cnnToIndex[cnn])]
      SegmentMatch(cnn, side, seg.geometry)
    }
  }

  private fun buildSamplePoints(coords: List<List<Double>>): List<Pair<Double, Double>> {
    val step = (coords.size / 10).coerceAtLeast(1)
    val indices = (0 until coords.size step step).toMutableList()
    if (!indices.contains(coords.size - 1)) indices.add(coords.size - 1)
    return indices.mapNotNull { i ->
      val c = coords[i]
      if (c.size >= 2) c[1] to c[0] else null
    }
  }

  private fun getCandidateIndices(lat: Double, lng: Double, cellSize: Double): List<Int> {
    val latCell = floor(lat / cellSize).toInt()
    val lngCell = floor(lng / cellSize).toInt()
    val result = mutableListOf<Int>()
    for (dLat in -1..1) {
      for (dLng in -1..1) {
        val key = (latCell + dLat).toLong().shl(32) or ((lngCell + dLng).toLong() and 0xFFFFFFFFL)
        spatialIndex[key]?.let { result.addAll(it) }
      }
    }
    return result
  }

  companion object {
    // Conservative approximation: ~90,000 meters per degree at SF latitude.
    // Used for cheap bounding-box rejection before expensive polyline distance.
    private const val METERS_PER_DEGREE = 90_000.0

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
  }
}
