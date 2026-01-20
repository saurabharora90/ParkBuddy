package dev.parkbuddy.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.StreetCleaningSegmentModel
import dev.bongballe.parkbuddy.model.StreetSide
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, MapsComposeExperimentalApi::class)
@Composable
fun MapScreen(viewModel: MapViewModel, onNavigateToWatchlist: () -> Unit) {
  val segments by viewModel.streetCleaningSegments.collectAsState()

  // San Francisco coordinates
  val sf = LatLng(37.7749, -122.4194)
  val sfBounds = LatLngBounds(LatLng(37.703397, -122.519967), LatLng(37.832396, -122.354979))
  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(sf, 15f)
  }

  val visibleSegments by remember {
    derivedStateOf {
      if (cameraPositionState.isMoving) {
        emptyList()
      } else {
        val visibleBounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
        if (cameraPositionState.position.zoom < 15f || visibleBounds == null) {
          emptyList()
        } else {
          segments.filter { segment ->
            val points = parseLocationData(segment.locationData)
            points.any { visibleBounds.contains(it) }
          }
        }
      }
    }
  }

  var selectedSegment by remember { mutableStateOf<StreetCleaningSegmentModel?>(null) }
  val sheetState = rememberModalBottomSheetState()

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    floatingActionButton = { Button(onClick = onNavigateToWatchlist) { Text("Watchlist") } },
  ) { innerPadding ->
    Box(modifier = Modifier.padding(innerPadding)) {
      GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties =
          MapProperties(
            isMyLocationEnabled = true,
            latLngBoundsForCameraTarget = sfBounds,
            minZoomPreference = 13f,
            maxZoomPreference = 20f,
          ),
      ) {
        visibleSegments.forEach { segment ->
          val basePoints = parseLocationData(segment.locationData)
          if (basePoints.isNotEmpty()) {
            // Offset polylines so LEFT and RIGHT sides of the same street don't overlap.
            // The API returns identical geometry for both sides, so we shift them apart.
            // LEFT side is offset to the left of the line direction, RIGHT to the right.
            // Offset distance is ~3 meters, enough to be visually distinct at street level zoom.
            val points = offsetPolyline(basePoints, segment.side)
            Polyline(
              points = points,
              color = if (segment.isWatched) Color.Green else Color.Red,
              width = 10f,
              clickable = true,
              onClick = { selectedSegment = segment },
            )
          }
        }
      }

      if (selectedSegment != null) {
        ModalBottomSheet(onDismissRequest = { selectedSegment = null }, sheetState = sheetState) {
          Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
            Text(
              text = selectedSegment?.streetName ?: "Street Cleaning Schedule",
              style = MaterialTheme.typography.headlineSmall,
            )
            selectedSegment
              ?.limits
              ?.takeIf { it.isNotBlank() }
              ?.let { limits ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = limits, style = MaterialTheme.typography.bodyMedium)
              }
            selectedSegment
              ?.blockSide
              ?.takeIf { it.isNotBlank() }
              ?.let { blockSide ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "$blockSide side",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = selectedSegment?.schedule ?: "Unknown Schedule",
              style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
              onClick = {
                selectedSegment?.let {
                  viewModel.toggleWatchStatus(it)
                  selectedSegment = null // Close sheet after action
                }
              }
            ) {
              Text(if (selectedSegment?.isWatched == true) "Unwatch" else "Watch")
            }
          }
        }
      }
    }
  }
}

fun parseLocationData(geometry: Geometry): List<LatLng> {
  return try {
    geometry.coordinates.map { point ->
      // GeoJSON is [Longitude, Latitude]
      // Google Maps LatLng is (Latitude, Longitude)
      LatLng(point[1], point[0])
    }
  } catch (ignore: Exception) {
    // Intentionally swallowing the exception here.
    // The geometry data from the SF API is sometimes malformed.
    // We'd rather render nothing than crash the app.
    emptyList()
  }
}

/**
 * Offsets a polyline perpendicular to its direction based on the street side.
 *
 * The SF street cleaning API returns identical geometry (the street centerline) for both LEFT and
 * RIGHT sides of the street. To make both sides visible and tappable on the map, we offset each
 * polyline perpendicular to its direction:
 * - LEFT side: offset to the left of the line's direction of travel
 * - RIGHT side: offset to the right of the line's direction of travel
 *
 * The offset distance (~3 meters) is chosen to be:
 * - Large enough to see two distinct parallel lines at typical zoom levels (15-20)
 * - Small enough that lines still appear to represent the same street
 *
 * Algorithm:
 * 1. For each point, calculate the bearing to the next point (or from previous if last)
 * 2. Rotate bearing 90° left or right depending on side
 * 3. Move the point in that perpendicular direction by the offset distance
 */
private fun offsetPolyline(points: List<LatLng>, side: StreetSide): List<LatLng> {
  if (points.size < 2) return points

  // Offset distance in meters. ~6m provides good visual separation for SF street widths.
  val offsetMeters = 6.0

  // Direction multiplier: LEFT = -1 (rotate bearing left), RIGHT = +1 (rotate bearing right)
  val direction = if (side == StreetSide.LEFT) -1.0 else 1.0

  return points.mapIndexed { index, point ->
    // Calculate bearing to next point (or from previous point if we're at the end)
    val bearing =
      if (index < points.size - 1) {
        calculateBearing(point, points[index + 1])
      } else {
        calculateBearing(points[index - 1], point)
      }

    // Rotate bearing 90 degrees perpendicular to the line direction
    val perpendicularBearing = bearing + (direction * 90.0)

    // Move point in the perpendicular direction
    movePoint(point, perpendicularBearing, offsetMeters)
  }
}

/**
 * Calculates the initial bearing (forward azimuth) from point1 to point2. Returns bearing in
 * degrees (0-360), where 0 = North, 90 = East, etc.
 */
private fun calculateBearing(from: LatLng, to: LatLng): Double {
  val lat1 = Math.toRadians(from.latitude)
  val lat2 = Math.toRadians(to.latitude)
  val deltaLon = Math.toRadians(to.longitude - from.longitude)

  val x = sin(deltaLon) * cos(lat2)
  val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)

  val bearing = Math.toDegrees(atan2(x, y))
  return (bearing + 360) % 360
}

/**
 * Moves a point by a given distance in a given bearing direction. Uses the haversine formula for
 * accuracy at small distances.
 *
 * @param point Starting point
 * @param bearing Direction in degrees (0 = North, 90 = East)
 * @param distanceMeters Distance to move in meters
 */
private fun movePoint(point: LatLng, bearing: Double, distanceMeters: Double): LatLng {
  // Earth's radius in meters
  val earthRadius = 6371000.0

  val lat1 = Math.toRadians(point.latitude)
  val lon1 = Math.toRadians(point.longitude)
  val bearingRad = Math.toRadians(bearing)

  // Angular distance traveled
  val angularDistance = distanceMeters / earthRadius

  val lat2 =
    Math.asin(sin(lat1) * cos(angularDistance) + cos(lat1) * sin(angularDistance) * cos(bearingRad))

  val lon2 =
    lon1 +
      atan2(
        sin(bearingRad) * sin(angularDistance) * cos(lat1),
        cos(angularDistance) - sin(lat1) * sin(lat2),
      )

  return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
}
