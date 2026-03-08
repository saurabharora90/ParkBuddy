package dev.parkbuddy.feature.onboarding.bluetooth

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.bongballe.parkbuddy.core.bluetooth.BluetoothDeviceUiModel
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.bongballe.parkbuddy.theme.SageContainer
import dev.bongballe.parkbuddy.theme.SageGreen
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.parkbuddy.core.ui.ParkBuddyAlertDialog
import dev.parkbuddy.core.ui.ParkBuddyButton
import dev.parkbuddy.core.ui.SquircleIcon
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun BluetoothDeviceSelectionScreen(
  viewModel: BluetoothDeviceSelectionViewModel = metroViewModel(),
  onDeviceSelected: () -> Unit,
) {
  val context = LocalContext.current

  // Re-check permission on every resume (user may return from Settings)
  var permissionCheckTrigger by remember { mutableIntStateOf(0) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionCheckTrigger++ }

  val btPermissions =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
      listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    }

  val hasBluetoothPermission =
    remember(permissionCheckTrigger) {
      btPermissions.all {
        ContextCompat.checkSelfPermission(context, it) ==
          android.content.pm.PackageManager.PERMISSION_GRANTED
      }
    }

  val activity = context as? Activity

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
      permissionCheckTrigger++
    }

  if (!hasBluetoothPermission) {
    BluetoothPermissionContent(
      onGrantClick = {
        val promptable =
          activity?.let { act ->
            btPermissions.filter { ActivityCompat.shouldShowRequestPermissionRationale(act, it) }
          }
        if (!promptable.isNullOrEmpty()) {
          permissionLauncher.launch(promptable.toTypedArray())
        } else {
          // First launch or permanently denied: try the runtime prompt.
          // If the system has already permanently denied, this is a no-op
          // and we fall through to the next tap sending them to Settings.
          val neverAsked =
            activity?.let { act ->
              btPermissions.any {
                ContextCompat.checkSelfPermission(act, it) !=
                  android.content.pm.PackageManager.PERMISSION_GRANTED &&
                  !ActivityCompat.shouldShowRequestPermissionRationale(act, it)
              }
            } ?: false
          if (neverAsked) {
            permissionLauncher.launch(btPermissions.toTypedArray())
          } else {
            context.startActivity(
              Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
              }
            )
          }
        }
      },
      onSkipClick = {
        viewModel.clearDeviceSelection()
        onDeviceSelected()
      },
    )
  } else {
    val uiState by viewModel.uiState.collectAsState()

    BluetoothDeviceSelectionScreenContent(
      uiState = uiState,
      onDeviceSelect = viewModel::selectDevice,
      onContinueClick = onDeviceSelected,
      onSkipClick = {
        viewModel.clearDeviceSelection()
        onDeviceSelected()
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BluetoothPermissionContent(onGrantClick: () -> Unit, onSkipClick: () -> Unit) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = "Select Your Car", style = MaterialTheme.typography.titleLarge) }
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { paddingValues ->
    Column(
      modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(modifier = Modifier.height(32.dp))

      SquircleIcon(
        icon = Icons.Default.BluetoothDisabled,
        size = 72.dp,
        shape = RoundedCornerShape(16.dp),
        iconTint = Terracotta,
        backgroundTint = Terracotta.copy(alpha = 0.12f),
      )

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "Detect your car automatically",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text =
          "When you disconnect from your car's Bluetooth, ParkBuddy knows you just parked " +
            "and checks the rules for that spot. No tapping, no typing.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.weight(1f))

      ParkBuddyButton(
        label = "Allow Bluetooth",
        onClick = onGrantClick,
        modifier = Modifier.fillMaxWidth(),
      )

      TextButton(onClick = onSkipClick, modifier = Modifier.padding(top = 8.dp)) {
        Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BluetoothDeviceSelectionScreenContent(
  uiState: BluetoothSelectionUiState,
  onDeviceSelect: (BluetoothDeviceUiModel) -> Unit,
  onContinueClick: () -> Unit,
  onSkipClick: () -> Unit,
) {
  var showSkipDialog by remember { mutableStateOf(false) }

  if (showSkipDialog) {
    ParkBuddyAlertDialog(
      title = "Skip Automatic Detection?",
      text =
        "Without a connected Bluetooth device, ParkBuddy can't automatically detect " +
          "when you park. You'll need to manually mark your location each time.",
      confirmLabel = "Cancel",
      dismissLabel = "Skip Anyway",
      onConfirm = { showSkipDialog = false },
      onDismiss = {
        showSkipDialog = false
        onSkipClick()
      },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = "Select Your Car", style = MaterialTheme.typography.titleLarge) }
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { paddingValues ->
    Column(
      modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      SquircleIcon(
        icon = Icons.Default.DirectionsCar,
        contentDescription = null,
        size = 72.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(top = 8.dp),
      )

      Text(
        text =
          "Select your vehicle from your paired devices. ParkBuddy uses this \"handshake\" " +
            "to know exactly when and where your parking session begins\u2014no manual input required.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // Device List
      if (uiState.devices.isEmpty()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
          Text(
            text = "No paired devices found.\nPlease check your Bluetooth settings.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
          items(uiState.devices) { device ->
            BluetoothDeviceCard(
              device = device,
              isSelected = device == uiState.selectedDevice,
              onClick = { onDeviceSelect(device) },
            )
          }
        }
      }

      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 8.dp),
      ) {
        ParkBuddyButton(
          label = "Save & Start Protecting",
          onClick = onContinueClick,
          modifier = Modifier.fillMaxWidth(),
          enabled = uiState.selectedDevice != null,
        )

        TextButton(onClick = { showSkipDialog = true }) {
          Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
private fun BluetoothDeviceCard(
  device: BluetoothDeviceUiModel,
  isSelected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors =
      CardDefaults.cardColors(
        containerColor = if (isSelected) SageContainer.copy(alpha = 0.3f) else Color.White
      ),
    border =
      BorderStroke(
        width = 1.dp,
        color =
          if (isSelected) SagePrimary
          else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
      ),
    onClick = { onClick() },
  ) {
    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier =
          Modifier.size(48.dp)
            .background(
              if (isSelected) SagePrimary else SageContainer.copy(alpha = 0.5f),
              RoundedCornerShape(16.dp),
            ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector =
            if (isSelected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
          contentDescription = null,
          tint = if (isSelected) Color.White else SageGreen,
          modifier = Modifier.size(24.dp),
        )
      }

      Column(modifier = Modifier.padding(horizontal = 16.dp).weight(1f)) {
        Text(
          text = device.name,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (isSelected) {
          Text(
            text = "Set as your parking trigger",
            style = MaterialTheme.typography.labelMedium,
            color = SagePrimary,
            fontWeight = FontWeight.Medium,
          )
        }
      }

      Icon(
        imageVector =
          if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
        contentDescription = if (isSelected) "Selected" else "Not Selected",
        tint = if (isSelected) SageGreen else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun BluetoothDeviceSelectionPreview() {
  ParkBuddyTheme {
    BluetoothDeviceSelectionScreenContent(
      uiState =
        BluetoothSelectionUiState(
          devices =
            listOf(
              BluetoothDeviceUiModel("Tesla Model 3", "00:00:00:00:00:00"),
              BluetoothDeviceUiModel("My Audi", "00:00:00:00:00:01"),
              BluetoothDeviceUiModel("JBL Flip 5", "00:00:00:00:00:02"),
            ),
          selectedDevice = BluetoothDeviceUiModel("Tesla Model 3", "00:00:00:00:00:00"),
        ),
      onDeviceSelect = {},
      onContinueClick = {},
      onSkipClick = {},
    )
  }
}
