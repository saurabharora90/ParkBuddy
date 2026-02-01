@file:OptIn(ExperimentalMaterial3Api::class)

package dev.parkbuddy.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.parkbuddy.core.ui.NestedScaffold
import dev.parkbuddy.core.ui.SquircleIcon
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun SettingsScreen(
  onNavigateToBluetooth: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = metroViewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()

  SettingsContent(
    uiState = uiState,
    onAutoTrackingToggle = viewModel::setAutoTrackingEnabled,
    onNavigateToBluetooth = onNavigateToBluetooth,
    onBuyMeACoffee = viewModel::buyMeACoffee,
    modifier = modifier,
  )
}

@Composable
private fun SettingsContent(
  uiState: SettingsUiState,
  onAutoTrackingToggle: (Boolean) -> Unit,
  onNavigateToBluetooth: () -> Unit,
  onBuyMeACoffee: () -> Unit,
  modifier: Modifier = Modifier,
) {
  NestedScaffold(
    topBar = { TopAppBar(title = { Text(text = "AccountSetting") }) },
    containerColor = MaterialTheme.colorScheme.background,
    modifier = modifier.fillMaxSize(),
  ) { innerPadding ->
    Column(
      modifier =
        modifier
          .scrollable(state = rememberScrollState(), orientation = Orientation.Vertical)
          .fillMaxHeight()
          .padding(innerPadding)
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      SectionHeader(text = "CAR CONNECTIVITY", color = SagePrimary)

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column {
          SettingRow(
            icon = Icons.Default.LocationOn,
            iconBackgroundColor = SagePrimary,
            iconTint = Color.White,
            title = "Car Tracking",
            subtitle = "Always monitor location",
            modifier = Modifier.padding(16.dp),
            trailingContent = {
              Switch(
                checked = uiState.isAutoTrackingEnabled,
                onCheckedChange = onAutoTrackingToggle,
                colors =
                  SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SagePrimary,
                  ),
              )
            },
            onClick = { onAutoTrackingToggle(!uiState.isAutoTrackingEnabled) },
          )

          Divider(modifier = Modifier.padding(horizontal = 16.dp))

          SettingRow(
            icon = Icons.Default.DirectionsCar,
            iconBackgroundColor = Color.White,
            iconTint = SagePrimary,
            title = "Connected Car",
            subtitle = uiState.bluetoothDeviceName ?: "My Main Vehicle",
            subtitleColor = SagePrimary,
            onClick = onNavigateToBluetooth,
            modifier = Modifier.padding(16.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      SectionHeader(text = "SUPPORT", color = Terracotta)

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column {
          SettingRow(
            icon = Icons.Default.Share,
            iconBackgroundColor = Terracotta,
            iconTint = Color.White,
            title = "Share ParkBuddy",
            onClick = {},
            modifier = Modifier.padding(16.dp),
          )

          Divider(modifier = Modifier.padding(horizontal = 16.dp))

          SettingRow(
            icon = Icons.Default.Favorite,
            iconBackgroundColor = Color.White,
            iconTint = Terracotta,
            title = "Buy me a coffee",
            trailingContent = {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
              )
            },
            onClick = onBuyMeACoffee,
            modifier = Modifier.padding(16.dp),
          )
        }
      }

      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Text(
          text = "ParkBuddy ${uiState.appVersion}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          letterSpacing = 1.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    }
  }
}

@Composable
private fun SectionHeader(text: String, color: Color) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = color,
    letterSpacing = 1.5.sp,
    fontWeight = FontWeight.Bold,
  )
}

@Composable
private fun SettingRow(
  icon: ImageVector,
  iconBackgroundColor: Color,
  iconTint: Color,
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  trailingContent: @Composable () -> Unit = {},
  onClick: (() -> Unit)? = null,
) {
  Row(
    modifier =
      Modifier.clickable(enabled = onClick != null, onClick = onClick ?: {})
        .then(modifier)
        .fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f),
    ) {
      SquircleIcon(
        icon = icon,
        size = 40.dp,
        shape = RoundedCornerShape(12.dp),
        iconTint = iconTint,
        backgroundTint = iconBackgroundColor,
      )

      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
        )
        subtitle?.let {
          Text(text = it, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
      }
    }

    trailingContent()
  }
}

@Composable
private fun Divider(modifier: Modifier = Modifier) {
  Spacer(modifier = modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.4f)))
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
  ParkBuddyTheme {
    SettingsContent(
      uiState =
        SettingsUiState(
          isAutoTrackingEnabled = true,
          bluetoothDeviceName = "My Car",
          appVersion = "V1.0",
        ),
      onAutoTrackingToggle = {},
      onNavigateToBluetooth = {},
      onBuyMeACoffee = {},
    )
  }
}
