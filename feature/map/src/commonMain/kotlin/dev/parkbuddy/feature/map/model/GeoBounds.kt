package dev.parkbuddy.feature.map.model

/** Platform-agnostic geographic boundaries. */
data class GeoBounds(val north: Double, val south: Double, val east: Double, val west: Double) {
  fun contains(latitude: Double, longitude: Double): Boolean {
    val latitudeInRange = latitude in south..north
    val longitudeInRange =
      if (west <= east) {
        longitude in west..east
      } else {
        longitude >= west || longitude <= east
      }
    return latitudeInRange && longitudeInRange
  }

  /**
   * Returns true if the line segment from (lat1, lng1) to (lat2, lng2) intersects this bounds.
   *
   * Uses Cohen-Sutherland outcodes: if both endpoints share a half-plane (both above, both below,
   * both left, or both right) the segment cannot cross the rectangle. Otherwise it might, and for
   * SF streets that's close enough (false positives just mean we show a spot that's barely off
   * screen, which is fine).
   */
  fun segmentMayIntersect(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Boolean {
    val code1 = outcode(lat1, lng1)
    val code2 = outcode(lat2, lng2)
    if (code1 == 0 || code2 == 0) return true
    return (code1 and code2) == 0
  }

  //   bit 0 = south, bit 1 = north, bit 2 = west, bit 3 = east
  private fun outcode(lat: Double, lng: Double): Int {
    var code = 0
    if (lat < south) code = code or 1 else if (lat > north) code = code or 2
    if (lng < west) code = code or 4 else if (lng > east) code = code or 8
    return code
  }
}
