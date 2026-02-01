package dev.parkbuddy.feature.map

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.theme.GoldenYellow
import dev.bongballe.parkbuddy.theme.SageGreen
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class, MapsComposeExperimentalApi::class, FlowPreview::class)
@Composable
fun MapScreen(modifier: Modifier = Modifier, viewModel: MapViewModel = metroViewModel()) {
  val state by viewModel.stateFlow.collectAsState()
  val spots = state.spots
  val watchedSpots = state.watchedSpots
  val parkedLocation = state.parkedLocation

  val watchedSpotIds = remember(watchedSpots) { watchedSpots.map { it.objectId }.toSet() }

  // Pre-compute LatLng points for all spots once
  val spotsWithPoints =
    remember(spots) { spots.map { spot -> spot to parseLocationData(spot.geometry) } }

  val sfBounds = LatLngBounds(LatLng(37.703397, -122.519967), LatLng(37.832396, -122.354979))
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val cameraPositionState = rememberCameraPositionState {
    val locationClient = LocationServices.getFusedLocationProviderClient(context)
    coroutineScope.launch {
      val location =
        try {
          locationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        } catch (_: Exception) {
          null
        }

      val startPosition =
        if (location != null && sfBounds.contains(LatLng(location.latitude, location.longitude))) {
          LatLng(location.latitude, location.longitude)
        } else {
          LatLng(37.7749, -122.4194) // Fallback to SF center
        }

      this@rememberCameraPositionState.animate(
        CameraUpdateFactory.newLatLngZoom(startPosition, 16f)
      )
    }
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
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  Box(modifier = modifier.fillMaxSize()) {
    GoogleMap(
      modifier = Modifier.fillMaxSize(),
      cameraPositionState = cameraPositionState,
      properties =
        MapProperties(
          isMyLocationEnabled = true,
          latLngBoundsForCameraTarget = sfBounds,
          minZoomPreference = 15f,
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

      parkedLocation?.let { (location, _) ->
        Marker(
          state = MarkerState(position = LatLng(location.latitude, location.longitude)),
          title = "Your Car",
          icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE),
          onClick = {
            viewModel.requestParkedLocationBottomSheet()
            true
          },
        )
      }
    }

    selectedSpot?.let {
      ModalBottomSheet(
        onDismissRequest = { selectedSpot = null },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
      ) {
        SpotDetailContent(
          spot = it,
          isWatched = it.objectId in watchedSpotIds,
          onParkHere = {
            viewModel.parkHere(it)
            selectedSpot = null
          },
        )
      }
    }

    var isShowingConfirmCarMovedPrompt by remember { mutableStateOf(false) }
    var isShowingClearParkedLocationPrompt by remember { mutableStateOf(false) }

    if (state.shouldShowParkedLocationBottomSheet) {
      ModalBottomSheet(
        onDismissRequest = { viewModel.dismissParkedLocationBottomSheet() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
      ) {
        ParkedSpotDetailContent(
          spot = state.parkedLocation!!.second,
          reminders = state.parkedLocation!!.third,
          onMovedCar = { isShowingConfirmCarMovedPrompt = true },
          onEndSession = { isShowingClearParkedLocationPrompt = true },
        )
      }
    }

    if (isShowingConfirmCarMovedPrompt) {
      AlertDialog(
        onDismissRequest = { isShowingConfirmCarMovedPrompt = false },
        title = { Text(text = "Are you sure?") },
        text = {
          Text(
            "Marking your card as moved will clear your parked location and cancel the reminders"
          )
        },
        confirmButton = {
          TextButton(
            onClick = {
              isShowingConfirmCarMovedPrompt = false
              viewModel.clearParkedLocation()
            }
          ) {
            Text("Yes")
          }
        },
        dismissButton = {
          TextButton(
            onClick = { isShowingConfirmCarMovedPrompt = false },
            colors = ButtonDefaults.textButtonColors(contentColor = Terracotta),
          ) {
            Text("No")
          }
        },
      )
    }

    if (isShowingClearParkedLocationPrompt) {
      AlertDialog(
        onDismissRequest = { isShowingClearParkedLocationPrompt = false },
        title = { Text(text = "Are you sure?") },
        text = {
          Text(
            "We are sorry for detecting the wrong location. Proceeding will clear this as parked location and cancel the reminders"
          )
        },
        confirmButton = {
          TextButton(
            onClick = {
              isShowingClearParkedLocationPrompt = false
              viewModel.clearParkedLocation()
            }
          ) {
            Text("Yes")
          }
        },
        dismissButton = {
          TextButton(
            onClick = { isShowingClearParkedLocationPrompt = false },
            colors = ButtonDefaults.textButtonColors(contentColor = Terracotta),
          ) {
            Text("No")
          }
        },
      )
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
