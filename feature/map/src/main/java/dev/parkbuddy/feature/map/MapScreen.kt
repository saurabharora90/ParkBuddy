package dev.parkbuddy.feature.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.StreetCleaningSegmentModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel, onNavigateToWatchlist: () -> Unit) {
  val segments by viewModel.streetCleaningSegments.collectAsState()
  val context = LocalContext.current

  // San Francisco coordinates
  val sf = LatLng(37.7749, -122.4194)
  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(sf, 12f)
  }

  var hasLocationPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val launcher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
      hasLocationPermission =
        permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
          permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

  LaunchedEffect(Unit) {
    if (!hasLocationPermission) {
      launcher.launch(
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
        )
      )
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
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
        uiSettings = MapUiSettings(myLocationButtonEnabled = true),
      ) {
        segments.forEach { segment ->
          val points = parseLocationData(segment.locationData)
          if (points.isNotEmpty()) {
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
            Text(text = "Street Cleaning Schedule", style = MaterialTheme.typography.headlineSmall)
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

// Helper to parse the Geometry to LatLng list
fun parseLocationData(geometry: Geometry): List<LatLng> {
  return try {
    geometry.coordinates.map { point ->
      // GeoJSON is [Longitude, Latitude]
      // Google Maps LatLng is (Latitude, Longitude)
      LatLng(point[1], point[0])
    }
  } catch (e: Exception) {
    emptyList()
  }
}
