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
import androidx.compose.ui.graphics.Color
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
import dev.bongballe.parkbuddy.core.navigation.MainRoute
import dev.bongballe.parkbuddy.core.navigation.Navigator
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.theme.Goldenrod
import dev.bongballe.parkbuddy.theme.SageGreen
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.bongballe.parkbuddy.theme.WildIris
import dev.parkbuddy.core.ui.BannerNudge
import dev.parkbuddy.core.ui.ParkBuddyAlertDialog
import dev.parkbuddy.core.ui.ParkBuddyIcons
import dev.parkbuddy.feature.map.model.GeoBounds
import dev.parkbuddy.feature.map.model.MapViewport
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val SF_BOUNDS = LatLngBounds(LatLng(37.703397, -122.519967), LatLng(37.832396, -122.354979))

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class, MapsComposeExperimentalApi::class, FlowPreview::class)
@Composable
actual fun MapScreen(navigator: Navigator, modifier: Modifier, viewModel: MapViewModel) {
  val state by viewModel.stateFlow.collectAsState()
  val parkedState = state.parkedState

  // Pre-compute LatLng points for visible spots
  val visibleSpotsWithPoints =
    remember(state.visibleSpots) {
      state.visibleSpots.map { spot -> spot to parseLocationData(spot.geometry) }
    }

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
        if (location != null && SF_BOUNDS.contains(LatLng(location.latitude, location.longitude))) {
          LatLng(location.latitude, location.longitude)
        } else {
          LatLng(37.7749, -122.4194) // Fallback to SF center
        }

      this@rememberCameraPositionState.animate(
        CameraUpdateFactory.newLatLngZoom(startPosition, 16f)
      )
    }
  }

  LaunchedEffect(Unit) {
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
        if (bounds != null) {
          viewModel.updateViewport(
            MapViewport(
              bounds =
                GeoBounds(
                  north = bounds.northeast.latitude,
                  south = bounds.southwest.latitude,
                  east = bounds.northeast.longitude,
                  west = bounds.southwest.longitude,
                ),
              zoom = zoom,
            )
          )
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
          latLngBoundsForCameraTarget = SF_BOUNDS,
          minZoomPreference = 15f,
          maxZoomPreference = 18f,
        ),
    ) {
      visibleSpotsWithPoints.forEach { (spot, points) ->
        if (points.isNotEmpty()) {
          val isInPermitZone = spot.rppAreas.contains(state.permitZone)
          val dominantColor = getDominantColor(spot, isInPermitZone)
          Polyline(
            points = points,
            color = dominantColor,
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
        leadingIcon = ParkBuddyIcons.Warning,
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
        dismissLabel = null,
        onDismiss = null,
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
      Icon(imageVector = ParkBuddyIcons.Info, contentDescription = "Map legend")
    }

    selectedSpot?.let {
      ModalBottomSheet(
        onDismissRequest = { selectedSpot = null },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
      ) {
        SpotDetailContent(
          spot = it,
          isInPermitZone = it.rppAreas.contains(state.permitZone),
          onParkHere = {
            viewModel.parkHere(it)
            selectedSpot = null
          },
        )
      }
    }

    // Zone setup nudge (one-off, persisted)
    AnimatedVisibility(
      visible = state.permitZone == null && !state.hasSeenZoneNudge,
      enter = slideInVertically { it },
      exit = slideOutVertically { it },
      modifier = Modifier.align(Alignment.BottomCenter),
    ) {
      BannerNudge(
        title = "Live in a permit zone?",
        subtitle = "Set it up so we know when you're exempt from time limits.",
        actionLabel = "Set up",
        onAction = { navigator.goTo(MainRoute(MainRoute.Tab.MY_ZONE)) },
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

    LegendRow(color = SageGreen, label = "Your permit zone")
    LegendRow(color = SagePrimary, label = "Free parking")
    LegendRow(color = Goldenrod, label = "Metered parking")
    LegendRow(color = WildIris, label = "Time-limited parking")
    LegendRow(color = Terracotta, label = "Restricted / No parking")

    Text(
      text = "Tap any colored line to see parking restrictions and street sweeping times.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
    )
  }
}

@Composable
private fun LegendRow(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(modifier = Modifier.padding(end = 12.dp).size(16.dp).background(color, CircleShape))
    Text(label, color = MaterialTheme.colorScheme.onSurface)
  }
}

/**
 * Picks a polyline color based on what's happening at the spot RIGHT NOW.
 *
 * Colors update as the map viewport refreshes (every 30s via tickerFlow), so a spot that's
 * commercial-only in the morning shows red, then switches to yellow (metered) in the afternoon.
 * Tapping the polyline opens SpotDetailContent which shows the full timeline.
 *
 * Permit zone spots always get SageGreen regardless of their timeline.
 */
private fun getDominantColor(
  spot: ParkingSpot,
  isInPermitZone: Boolean,
  now: kotlin.time.Instant = kotlin.time.Clock.System.now(),
): Color {
  if (isInPermitZone) return SageGreen
  if (spot.timeline.isEmpty()) return SagePrimary

  // Check if any sweeping is active right now
  val sweepingActive = spot.sweepingSchedules.any { it.isWithinWindow(now) }
  if (sweepingActive) return Terracotta

  // Find the currently active timeline interval
  val activeType = spot.timeline.firstOrNull { it.isActiveAt(now) }?.type
  return when (activeType) {
    is IntervalType.Forbidden -> Terracotta
    is IntervalType.Restricted -> Terracotta
    is IntervalType.Metered -> Goldenrod
    is IntervalType.Limited -> WildIris
    // No active interval = free parking right now
    is IntervalType.Open,
    null -> SagePrimary
  }
}

private fun parseLocationData(geometry: Geometry): List<LatLng> {
  return try {
    geometry.coordinates.map { point -> LatLng(point[1], point[0]) }
  } catch (ignore: Exception) {
    emptyList()
  }
}
