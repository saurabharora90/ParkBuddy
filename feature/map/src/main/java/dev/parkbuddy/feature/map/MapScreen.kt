package dev.parkbuddy.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.theme.GoldenYellow
import dev.bongballe.parkbuddy.theme.SageGreen
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class, MapsComposeExperimentalApi::class, FlowPreview::class)
@Composable
fun MapScreen(viewModel: MapViewModel, onNavigateToWatchlist: () -> Unit) {
  val spots by viewModel.parkingSpots.collectAsState()
  val watchedSpots by viewModel.watchedSpots.collectAsState()

  val watchedSpotIds = remember(watchedSpots) { watchedSpots.map { it.objectId }.toSet() }

  // Pre-compute LatLng points for all spots once
  val spotsWithPoints =
    remember(spots) { spots.map { spot -> spot to parseLocationData(spot.geometry) } }

  val sf = LatLng(37.7749, -122.4194)
  val sfBounds = LatLngBounds(LatLng(37.703397, -122.519967), LatLng(37.832396, -122.354979))
  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(sf, 15f)
  }

  // Use state + LaunchedEffect with debounce instead of derivedStateOf
  var visibleSpots by remember {
    mutableStateOf<List<Pair<ParkingSpot, List<LatLng>>>>(emptyList())
  }

  LaunchedEffect(spotsWithPoints) {
    snapshotFlow {
        Triple(
          cameraPositionState.isMoving,
          cameraPositionState.position.zoom,
          cameraPositionState.projection?.visibleRegion?.latLngBounds,
        )
      }
      .filter { (isMoving, _, _) -> !isMoving }
      .debounce(100)
      .distinctUntilChanged()
      .collect { (_, zoom, bounds) ->
        visibleSpots =
          if (zoom < 15f || bounds == null) {
            emptyList()
          } else {
            spotsWithPoints.filter { (_, points) -> points.any { bounds.contains(it) } }
          }
      }
  }

  var selectedSpot by remember { mutableStateOf<ParkingSpot?>(null) }
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
            maxZoomPreference = 18f,
          ),
      ) {
        visibleSpots.forEach { (spot, points) ->
          if (points.isNotEmpty()) {
            val isWatched = spot.objectId in watchedSpotIds
            Polyline(
              points = points,
              color = if (isWatched) SageGreen else GoldenYellow,
              width = if (isWatched) 12f else 8f,
              clickable = true,
              onClick = { selectedSpot = spot },
            )
          }
        }
      }

      if (selectedSpot != null) {
        ModalBottomSheet(
          onDismissRequest = { selectedSpot = null },
          sheetState = sheetState,
          containerColor = MaterialTheme.colorScheme.background,
        ) {
          SpotDetailContent(
            spot = selectedSpot!!,
            isWatched = selectedSpot!!.objectId in watchedSpotIds,
          )
        }
      }
    }
  }
}

fun parseLocationData(geometry: Geometry): List<LatLng> {
  return try {
    geometry.coordinates.map { point -> LatLng(point[1], point[0]) }
  } catch (ignore: Exception) {
    emptyList()
  }
}
