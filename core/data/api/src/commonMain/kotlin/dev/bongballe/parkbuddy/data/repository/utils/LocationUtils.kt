package dev.bongballe.parkbuddy.data.repository.utils

import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.StreetSide
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

private fun toRadians(degrees: Double): Double = degrees * PI / 180.0

object LocationUtils {
  private const val EARTH_RADIUS = 6371000.0

  fun calculateDistanceToPolyline(latitude: Double, longitude: Double, geometry: Geometry): Double {
    val points = geometry.coordinates
    if (points.isEmpty()) return Double.MAX_VALUE

    var minDistance = Double.MAX_VALUE
    for (i in 0 until points.size - 1) {
      val p1 = points[i]
      val p2 = points[i + 1]
      if (p1.size < 2 || p2.size < 2) continue
      val dist = distanceToSegment(latitude, longitude, p1[1], p1[0], p2[1], p2[0])
      if (dist < minDistance) {
        minDistance = dist
      }
    }
    return minDistance
  }

  /**
   * Determine which side of the street centerline the point is on.
   *
   * Projects the point onto the closest segment of the polyline, then uses the cross product of
   * that segment's direction vector with the point-to-segment vector. This is accurate even on
   * curved streets because it uses the local tangent at the closest point rather than a single
   * first-to-last vector.
   *
   * centerline direction ──► ╲ ╲ cross > 0 → LEFT ╲ ─────────●──────────── centerline ╱ ╱ cross < 0
   * → RIGHT ╱
   */
  fun determineSide(latitude: Double, longitude: Double, lineGeometry: Geometry): StreetSide {
    val coords = lineGeometry.coordinates
    if (coords.size < 2) return StreetSide.RIGHT

    var bestSegIdx = 0
    var bestDist = Double.MAX_VALUE

    for (i in 0 until coords.size - 1) {
      val a = coords[i]
      val b = coords[i + 1]
      if (a.size < 2 || b.size < 2) continue
      val dist = distanceToSegment(latitude, longitude, a[1], a[0], b[1], b[0])
      if (dist < bestDist) {
        bestDist = dist
        bestSegIdx = i
      }
    }

    val p1 = coords[bestSegIdx]
    val p2 = coords[bestSegIdx + 1]
    if (p1.size < 2 || p2.size < 2) return StreetSide.RIGHT

    val lngFactor = cos(toRadians((p1[1] + p2[1]) / 2.0))
    val dx = (p2[0] - p1[0]) * lngFactor
    val dy = p2[1] - p1[1]
    val px = (longitude - p1[0]) * lngFactor
    val py = latitude - p1[1]

    val cross = dx * py - dy * px
    return if (cross > 0) StreetSide.LEFT else StreetSide.RIGHT
  }

  private fun distanceToSegment(
    lat: Double,
    lng: Double,
    lat1: Double,
    lng1: Double,
    lat2: Double,
    lng2: Double,
  ): Double {
    val rLat1 = toRadians(lat1)
    val rLat2 = toRadians(lat2)
    val rLat = toRadians(lat)

    val dx = (toRadians(lng2) - toRadians(lng1)) * cos((rLat1 + rLat2) / 2)
    val dy = toRadians(lat2) - rLat1
    val x = (toRadians(lng) - toRadians(lng1)) * cos((rLat1 + rLat) / 2)
    val y = toRadians(lat) - rLat1

    val dot = x * dx + y * dy
    val lenSq = dx * dx + dy * dy
    val param = if (lenSq != 0.0) dot / lenSq else -1.0

    val xx: Double
    val yy: Double
    if (param < 0) {
      xx = 0.0
      yy = 0.0
    } else if (param > 1) {
      xx = dx
      yy = dy
    } else {
      xx = dx * param
      yy = dy * param
    }

    val diffX = x - xx
    val diffY = y - yy
    return sqrt(diffX * diffX + diffY * diffY) * EARTH_RADIUS
  }
}
