package dev.parkbuddy.feature.map

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import dev.bongballe.parkbuddy.theme.Goldenrod
import dev.bongballe.parkbuddy.theme.SageGreen
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.bongballe.parkbuddy.theme.WildIris
import dev.parkbuddy.core.ui.BannerNudge
import dev.parkbuddy.core.ui.ParkBuddyAlertDialog
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
fun MapScreen(
  onNavigateToZone: () -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: MapViewModel = metroViewModel(),
) {
  val state by viewModel.stateFlow.collectAsState()
  val spots = state.spots
  val permitSpots = state.permitSpots
  val parkedState = state.parkedState

  val permitSpotIds = remember(permitSpots) { permitSpots.map { it.objectId }.toSet() }

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

  // Permission banner: re-check on every resume in case user granted from Settings
  var permissionCheckTrigger by remember { mutableIntStateOf(0) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionCheckTrigger++ }

  val missingPermissions =
    remember(permissionCheckTrigger) { PermissionChecker.getMissingPermissionLabels(context) }

  val activity = context as? Activity

  // Launcher for re-prompting permissions that aren't permanently denied
  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
      permissionCheckTrigger++
    }

  var bannerDismissed by remember { mutableStateOf(false) }

  var selectedSpot by remember { mutableStateOf<ParkingSpot?>(null) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var showLegendSheet by remember { mutableStateOf(false) }

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
          val isInPermitZone = spot.objectId in permitSpotIds
          val isPaid = spot.regulation.requiresPayment
          Polyline(
            points = points,
            color = if (isInPermitZone) SageGreen else if (isPaid) Goldenrod else WildIris,
            width = if (isInPermitZone) 12f else 8f,
            clickable = true,
            onClick = { selectedSpot = spot },
          )
        }
      }

      parkedState?.let { ps ->
        Marker(
          state =
            MarkerState(
              position =
                LatLng(ps.parkedLocation.location.latitude, ps.parkedLocation.location.longitude)
            ),
          title = "Your Car",
          icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE),
          onClick = {
            viewModel.requestParkedLocationBottomSheet()
            true
          },
        )
      }
    }

    // Top banner: permission warning or "all set" welcome
    AnimatedVisibility(
      visible = missingPermissions.isNotEmpty() && !bannerDismissed,
      enter = slideInVertically { -it },
      exit = slideOutVertically { -it },
      modifier = Modifier.align(Alignment.TopCenter),
    ) {
      BannerNudge(
        title = "Some features are limited",
        subtitle = "Missing: ${missingPermissions.joinToString(", ")}",
        actionLabel = "Fix",
        onAction = {
          val promptable = activity?.let { PermissionChecker.getPromptablePermissions(it) }
          if (!promptable.isNullOrEmpty()) {
            permissionLauncher.launch(promptable.toTypedArray())
          } else {
            context.startActivity(
              Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
              }
            )
          }
        },
        dismissLabel = "Later",
        onDismiss = { bannerDismissed = true },
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        leadingIcon = Icons.Default.Warning,
      )
    }

    AnimatedVisibility(
      visible = missingPermissions.isEmpty() && !state.hasSeenMapNux && !bannerDismissed,
      enter = slideInVertically { -it },
      exit = slideOutVertically { -it },
      modifier = Modifier.align(Alignment.TopCenter),
    ) {
      BannerNudge(
        title = "You're all set!",
        subtitle = "Next time you park, ParkBuddy handles the rest.",
        actionLabel = "Nice",
        onAction = { viewModel.markMapNuxSeen() },
        dismissLabel = "Dismiss",
        onDismiss = { viewModel.markMapNuxSeen() },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }

    // Legend button
    IconButton(
      onClick = { showLegendSheet = true },
      colors =
        IconButtonDefaults.iconButtonColors(
          containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
          contentColor = SagePrimary,
        ),
      modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp),
    ) {
      Icon(imageVector = Icons.Default.Info, contentDescription = "Map legend")
    }

    selectedSpot?.let {
      ModalBottomSheet(
        onDismissRequest = { selectedSpot = null },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
      ) {
        SpotDetailContent(
          spot = it,
          isInPermitZone = it.objectId in permitSpotIds,
          onParkHere = {
            viewModel.parkHere(it)
            selectedSpot = null
          },
        )
      }
    }

    // Zone setup nudge (one-off, persisted)
    AnimatedVisibility(
      visible = !state.hasPermitZone && !state.hasSeenZoneNudge,
      enter = slideInVertically { it },
      exit = slideOutVertically { it },
      modifier = Modifier.align(Alignment.BottomCenter),
    ) {
      BannerNudge(
        title = "Live in a permit zone?",
        subtitle = "Set it up so we know when you're exempt from time limits.",
        actionLabel = "Set up",
        onAction = onNavigateToZone,
        dismissLabel = "Dismiss",
        onDismiss = { viewModel.dismissZoneNudge() },
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }

    var isShowingConfirmCarMovedPrompt by remember { mutableStateOf(false) }
    var isShowingClearParkedLocationPrompt by remember { mutableStateOf(false) }

    if (state.shouldShowParkedLocationBottomSheet && parkedState != null) {
      ModalBottomSheet(
        onDismissRequest = { viewModel.dismissParkedLocationBottomSheet() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
      ) {
        ParkedSpotDetailContent(
          spot = parkedState.spot,
          restrictionState = parkedState.restrictionState,
          reminders = parkedState.reminders,
          onMovedCar = { isShowingConfirmCarMovedPrompt = true },
          onEndSession = { isShowingClearParkedLocationPrompt = true },
        )
      }
    }

    if (isShowingConfirmCarMovedPrompt) {
      ParkBuddyAlertDialog(
        title = "Are you sure?",
        text =
          "Marking your car as moved will clear your parked location and cancel the reminders.",
        confirmLabel = "Yes",
        dismissLabel = "No",
        onConfirm = {
          coroutineScope.launch { sheetState.hide() }
          isShowingConfirmCarMovedPrompt = false
          viewModel.clearParkedLocation()
        },
        onDismiss = {
          coroutineScope.launch { sheetState.hide() }
          isShowingConfirmCarMovedPrompt = false
        },
      )
    }

    if (isShowingClearParkedLocationPrompt) {
      ParkBuddyAlertDialog(
        title = "Are you sure?",
        text =
          "We are sorry for detecting the wrong location. " +
            "Proceeding will clear this as parked location and cancel the reminders.",
        confirmLabel = "Yes",
        dismissLabel = "No",
        onConfirm = {
          coroutineScope.launch { sheetState.hide() }
          isShowingClearParkedLocationPrompt = false
          viewModel.reportWrongLocation()
        },
        onDismiss = {
          coroutineScope.launch { sheetState.hide() }
          isShowingClearParkedLocationPrompt = false
        },
      )
    }

    // Legend bottom sheet
    if (showLegendSheet) {
      ModalBottomSheet(
        onDismissRequest = { showLegendSheet = false },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
      ) {
        LegendContent()
      }
    }
  }
}

@Composable
private fun LegendContent(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxWidth().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("Map Legend", style = MaterialTheme.typography.titleMedium)

    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.padding(end = 12.dp).size(16.dp).background(SageGreen, CircleShape))
      Text("Your permit zone (safe to park).", color = MaterialTheme.colorScheme.onSurface)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.padding(end = 12.dp).size(16.dp).background(Goldenrod, CircleShape))
      Text("Paid parking meters.", color = MaterialTheme.colorScheme.onSurface)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.padding(end = 12.dp).size(16.dp).background(WildIris, CircleShape))
      Text("Time limits or other rules.", color = MaterialTheme.colorScheme.onSurface)
    }

    Text(
      text = "Tap any colored line to see parking restrictions and street sweeping times.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
    )
  }
}

private fun parseLocationData(geometry: Geometry): List<LatLng> {
  return try {
    geometry.coordinates.map { point -> LatLng(point[1], point[0]) }
  } catch (ignore: Exception) {
    emptyList()
  }
}
