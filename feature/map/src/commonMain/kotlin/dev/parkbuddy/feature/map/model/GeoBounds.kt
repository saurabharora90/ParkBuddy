package dev.parkbuddy.feature.map.model

/** Platform-agnostic geographic boundaries. */
data class GeoBounds(val north: Double, val south: Double, val east: Double, val west: Double) {
  fun contains(latitude: Double, longitude: Double): Boolean {
    val latitudeInRange = latitude in south..north
    val longitudeInRange =
      if (west <= east) {
        longitude in west..east
      } else {
        // Handles the 180th meridian (Anti-meridian) crossing
        longitude >= west || longitude <= east
      }
    return latitudeInRange && longitudeInRange
  }
}
