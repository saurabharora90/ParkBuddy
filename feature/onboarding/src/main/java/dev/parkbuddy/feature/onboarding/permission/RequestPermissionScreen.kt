package dev.parkbuddy.feature.onboarding.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.bongballe.parkbuddy.theme.SageContainer
import dev.bongballe.parkbuddy.theme.SageGreen
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.parkbuddy.core.ui.SquircleIcon

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestPermissionScreen(
  viewModel: RequestPermissionViewModel = viewModel(),
  onPermissionsGranted: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  var showRationale by remember { mutableStateOf(false) }

  if (uiState.areAllPermissionsGranted) {
    onPermissionsGranted()
  }
  val context = LocalContext.current

  // Foreground Permissions (Location & Bluetooth)
  val foregroundPermissionsToRequest = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      add(Manifest.permission.BLUETOOTH_SCAN)
      add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
      add(Manifest.permission.BLUETOOTH)
      add(Manifest.permission.BLUETOOTH_ADMIN)
    }
  }

  val foregroundPermissionsState =
    rememberMultiplePermissionsState(permissions = foregroundPermissionsToRequest)

  // Background Location Permission (Android Q+)
  // We request this separately/after foreground is granted on Android 11+
  val backgroundLocationPermissionState =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
      null
    }

  // Update ViewModel when permission state changes
  LaunchedEffect(
    foregroundPermissionsState.allPermissionsGranted,
    foregroundPermissionsState.permissions,
    backgroundLocationPermissionState?.status,
  ) {
    // Check Fine Location
    val fineLocationGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    // Check Bluetooth
    val bluetoothPermissions =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
      } else {
        listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
      }

    val bluetoothGranted =
      bluetoothPermissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
      }

    viewModel.updatePermissionState(
      isFineLocationGranted = fineLocationGranted,
      isBluetoothGranted = bluetoothGranted,
    )

    if (
      fineLocationGranted && backgroundLocationPermissionState?.status is PermissionStatus.Denied
    ) {
      showRationale = true
    }
  }

  LaunchedEffect(backgroundLocationPermissionState?.status) {
    val fineLocationGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    // Check Background Location (only if version requires it, effectively always true for older
    // versions if fine is granted in manifest legacy way, but for Q+ we check)
    val backgroundLocationGranted =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
      } else {
        // Pre-Q, background location was implicitly granted with fine location
        fineLocationGranted
      }

    viewModel.updateBackgroundLocationState(backgroundLocationGranted)
  }

  RequestPermissionScreenContent(
    uiState = uiState,
    showRationale = showRationale,
    onDismissRationale = { showRationale = false },
    onConfirmRationale = {
      showRationale = false
      backgroundLocationPermissionState?.launchPermissionRequest()
    },
    onContinueClick = {
      when {
        !uiState.isFineLocationGranted || !uiState.isBluetoothGranted -> {
          // First, request foreground permissions (Location & Bluetooth)
          foregroundPermissionsState.launchMultiplePermissionRequest()
        }

        !uiState.isBackgroundLocationGranted -> {
          backgroundLocationPermissionState?.launchPermissionRequest()
        }
      }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestPermissionScreenContent(
  uiState: OnboardingUiState,
  showRationale: Boolean,
  onDismissRationale: () -> Unit,
  onConfirmRationale: () -> Unit,
  onContinueClick: () -> Unit,
) {
  if (showRationale) {
    AlertDialog(
      onDismissRequest = onDismissRationale,
      title = { Text(text = "Permissions Required") },
      text = {
        Text(
          "To provide zero-touch parking alerts, ParkBuddy needs to access your location even when the app is closed or not in use.\n\n Your location data is only used locally to check for cleaning rules and is never shared."
        )
      },
      confirmButton = { TextButton(onClick = onConfirmRationale) { Text("OK") } },
      dismissButton = { TextButton(onClick = onDismissRationale) { Text("Cancel") } },
      containerColor = Color.White,
      titleContentColor = MaterialTheme.colorScheme.onSurface,
      textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = "Permissions Setup", style = MaterialTheme.typography.titleLarge) }
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { paddingValues ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(paddingValues)
          .padding(horizontal = 24.dp)
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
        contentAlignment = Alignment.Center,
      ) {
        SquircleIcon(
          icon = Icons.Default.LocalParking,
          size = 120.dp,
          shape = RoundedCornerShape(24.dp),
        )

        // Smaller Icons
        Row(
          modifier = Modifier.offset(y = 60.dp), // Adjust position to match design
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          SmallIconCard(icon = Icons.Default.MyLocation)
          SmallIconCard(icon = Icons.Default.Bluetooth)
        }
      }

      // Text Content
      Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        Text(
          text = "Hands-Free Protection",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(
          text =
            "To make the magic happen without you ever opening the app, ParkBuddy needs to stay \"in the loop\" with your car. We’ll automatically note your spot and only alert you if a tow truck or sweeper is scheduled.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Permission Cards
      Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        PermissionCard(
          icon = Icons.Default.LocationOn,
          title = "Location Access",
          description =
            "Required to find the specific cleaning rules for your exact parking coordinates.",
          isGranted = uiState.isFineLocationGranted && uiState.isBackgroundLocationGranted,
        )
        PermissionCard(
          icon = Icons.AutoMirrored.Filled.BluetoothSearching,
          title = "Nearby Devices",
          description =
            "We use your car’s Bluetooth as a \"handshake\" to know exactly when and where your parking session begins.",
          isGranted = uiState.isBluetoothGranted,
        )
      }

      Spacer(modifier = Modifier.weight(1f))
      Spacer(modifier = Modifier.height(32.dp))

      // Buttons
      Button(
        onClick = onContinueClick,
        modifier =
          Modifier.fillMaxWidth()
            .height(64.dp)
            .shadow(
              elevation = 8.dp,
              spotColor = SagePrimary.copy(alpha = 0.2f),
              shape = CircleShape,
            ),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = SagePrimary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        shape = CircleShape,
      ) {
        Text(
          text = "Enable Permissions",
          style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
        )
      }

      Text(
        text = "STANDARD SYSTEM PROMPTS WILL FOLLOW",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
          ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun SmallIconCard(icon: ImageVector) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.elevatedCardElevation(),
  ) {
    Icon(
      imageVector = icon,
      tint = MaterialTheme.colorScheme.primary,
      contentDescription = null,
      modifier = Modifier.size(48.dp).padding(12.dp),
    )
  }
}

@Composable
private fun PermissionCard(
  icon: ImageVector,
  title: String,
  description: String,
  isGranted: Boolean,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(28.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border =
      BorderStroke(
        width = 1.dp,
        color =
          if (isGranted) SagePrimary.copy(alpha = 0.5f)
          else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(56.dp)
            .background(SageContainer.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = SageGreen,
          modifier = Modifier.size(32.dp),
        )
      }

      Column(modifier = Modifier.padding(horizontal = 16.dp).weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Icon(
        imageVector =
          if (isGranted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
        contentDescription = if (isGranted) "Granted" else "Not Granted",
        tint = if (isGranted) SageGreen else SageGreen.copy(alpha = 0.3f),
        modifier = Modifier.size(28.dp),
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun RequestPermissionScreenPreview() {
  ParkBuddyTheme {
    RequestPermissionScreenContent(
      uiState = OnboardingUiState(),
      showRationale = false,
      onContinueClick = {},
      onDismissRationale = {},
      onConfirmRationale = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun RequestPermissionScreenGrantedPreview() {
  ParkBuddyTheme {
    RequestPermissionScreenContent(
      uiState =
        OnboardingUiState(
          isFineLocationGranted = true,
          isBackgroundLocationGranted = true,
          isBluetoothGranted = true,
        ),
      showRationale = false,
      onContinueClick = {},
      onDismissRationale = {},
      onConfirmRationale = {},
    )
  }
}
