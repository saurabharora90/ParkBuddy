package dev.bongballe.parkbuddy.data.repository.utils

import dev.bongballe.parkbuddy.model.Geometry
import kotlin.math.cos
import kotlin.math.sqrt

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

  private fun distanceToSegment(
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
