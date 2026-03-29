package dev.bongballe.parkbuddy.util

import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.Location

/** Returns the (lat, lng) centroid of this geometry's coordinates, or null if empty. */
fun Geometry.center(): Pair<Double, Double>? {
  if (coordinates.isEmpty()) return null
  var sumLat = 0.0
  var sumLng = 0.0
  var count = 0
  for (coord in coordinates) {
    if (coord.size >= 2) {
      sumLng += coord[0]
      sumLat += coord[1]
      count++
    }
  }
  if (count == 0) return null
  return (sumLat / count) to (sumLng / count)
}

/** Returns the centroid as a [Location], or null if empty. */
fun Geometry.centerLocation(): Location? {
  val (lat, lng) = center() ?: return null
  return Location(lat, lng)
}
