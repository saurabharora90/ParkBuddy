package dev.parkbuddy.feature.onboarding.bluetooth

import android.Manifest
import android.os.Build
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dev.bongballe.parkbuddy.core.bluetooth.BluetoothDeviceUiModel
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.bongballe.parkbuddy.theme.SageContainer
import dev.bongballe.parkbuddy.theme.SageGreen
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.parkbuddy.core.ui.ParkBuddyButton
import dev.parkbuddy.core.ui.SquircleIcon
import dev.zacsweers.metrox.viewmodel.metroViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BluetoothDeviceSelectionScreen(
  viewModel: BluetoothDeviceSelectionViewModel = metroViewModel(),
  onDeviceSelected: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()

  val notificationPermissionState =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS) { isGranted ->
        if (isGranted) {
          onDeviceSelected()
        } else {
          // Proceed anyway, or handle denial. For now, we proceed.
          onDeviceSelected()
        }
      }
    } else {
      null
    }

  BluetoothDeviceSelectionScreenContent(
    uiState = uiState,
    onDeviceSelect = viewModel::selectDevice,
    onContinueClick = {
      if (notificationPermissionState != null && !notificationPermissionState.status.isGranted) {
        notificationPermissionState.launchPermissionRequest()
      } else {
        onDeviceSelected()
      }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDeviceSelectionScreenContent(
  uiState: BluetoothSelectionUiState,
  onDeviceSelect: (BluetoothDeviceUiModel) -> Unit,
  onContinueClick: () -> Unit,
) {
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
            "to know exactly when and where your parking session begins—no manual input required.",
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

      ParkBuddyButton(
        label = "Save & Start Protecting",
        onClick = onContinueClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = uiState.selectedDevice != null,
      )

      Spacer(modifier = Modifier.height(24.dp))
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
    )
  }
}
