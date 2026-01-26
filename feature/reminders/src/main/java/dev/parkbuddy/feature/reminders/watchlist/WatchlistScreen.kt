package dev.parkbuddy.feature.reminders.watchlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditRoad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.parkbuddy.core.ui.SquircleIcon
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun WatchlistScreen(viewModel: WatchlistViewModel = metroViewModel()) {
  val availableZones by viewModel.availableZones.collectAsState()
  val selectedZone by viewModel.selectedZone.collectAsState()
  val watchedSpotCount by viewModel.watchedSpotCount.collectAsState()
  val watchedSpots by viewModel.watchedSpots.collectAsState()
  val reminders by viewModel.reminders.collectAsState()
  val isZonePickerExpanded by viewModel.isZonePickerExpanded.collectAsState()

  WatchlistContent(
    availableZones = availableZones,
    selectedZone = selectedZone,
    watchedSpotCount = watchedSpotCount,
    watchedSpots = watchedSpots,
    reminders = reminders,
    isZonePickerExpanded = isZonePickerExpanded,
    onZonePickerExpandedChange = viewModel::setZonePickerExpanded,
    onZoneSelected = viewModel::selectZone,
    onAddReminder = viewModel::addReminder,
    onRemoveReminder = viewModel::removeReminder,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistContent(
  availableZones: List<String>,
  selectedZone: String?,
  watchedSpotCount: Int,
  watchedSpots: List<ParkingSpot>,
  reminders: List<Int>,
  isZonePickerExpanded: Boolean,
  onZonePickerExpandedChange: (Boolean) -> Unit,
  onZoneSelected: (String?) -> Unit,
  onAddReminder: (Int, Int) -> Unit,
  onRemoveReminder: (Int) -> Unit,
) {
  var showAddReminderDialog by remember { mutableStateOf(false) }

  if (showAddReminderDialog) {
    AddReminderDialog(
      onDismiss = { showAddReminderDialog = false },
      onConfirm = { h, m ->
        onAddReminder(h, m)
        showAddReminderDialog = false
      },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(title = { Text(text = "Your Parking Zone", modifier = Modifier.fillMaxWidth()) })
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        ZoneSelectorCard(
          availableZones = availableZones,
          selectedZone = selectedZone,
          watchedSpotCount = watchedSpotCount,
          isExpanded = isZonePickerExpanded,
          onExpandedChange = onZonePickerExpandedChange,
          onZoneSelected = onZoneSelected,
        )
      }

      if (selectedZone != null) {
        item {
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              "REMINDER RULES",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.titleMedium,
            )
            if (reminders.size < 5) {
              IconButton(onClick = { showAddReminderDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Reminder")
              }
            }
          }
        }

        if (reminders.isEmpty()) {
          item { Text("No reminders set.", style = MaterialTheme.typography.bodyMedium) }
        } else {
          items(reminders) { minutes ->
            ReminderItem(minutes = minutes, onDelete = { onRemoveReminder(minutes) })
          }
        }

        item {
          Text(
            "WATCHED STREETS",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
          )
        }

        items(watchedSpots) { spot -> WatchedStreetItem(spot = spot) }
      }

      if (selectedZone == null) {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.fillMaxWidth(),
            ) {
              SquircleIcon(
                icon = Icons.Default.LocationCity,
                contentDescription = null,
                size = 96.dp,
              )

              Spacer(modifier = Modifier.height(24.dp))

              Text(
                text = "Select Your Permit Zone",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
              )

              Spacer(modifier = Modifier.height(8.dp))

              Text(
                text =
                  "Choose your residential parking permit zone to automatically watch all streets in your area and receive cleaning reminders.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ZoneSelectorCard(
  availableZones: List<String>,
  selectedZone: String?,
  watchedSpotCount: Int,
  isExpanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  onZoneSelected: (String?) -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Parking Permit Zone",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
      )

      Spacer(modifier = Modifier.height(8.dp))

      Box {
        Button(
          onClick = { onExpandedChange(!isExpanded) },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(
            text = selectedZone?.let { "Zone $it" } ?: "Select a zone",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
          )
          Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select zone")
        }

        DropdownMenu(expanded = isExpanded, onDismissRequest = { onExpandedChange(false) }) {
          DropdownMenuItem(
            text = { Text("None (Clear selection)") },
            onClick = { onZoneSelected(null) },
          )
          availableZones.forEach { zone ->
            DropdownMenuItem(text = { Text("Zone $zone") }, onClick = { onZoneSelected(zone) })
          }
        }
      }

      if (selectedZone != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "$watchedSpotCount streets auto-watched",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
    }
  }
}

@Composable
fun ReminderItem(minutes: Int, onDelete: () -> Unit) {
  val hours = minutes / 60
  val mins = minutes % 60
  val timeString = if (hours > 0) "$hours hr ${mins} min before" else "$mins min before"

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    border =
      BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
  ) {
    Row(
      modifier = Modifier.padding(16.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SquircleIcon(
        icon = Icons.Default.NotificationsActive,
        contentDescription = null,
        size = 48.dp,
        shape = RoundedCornerShape(16.dp),
        iconTint = Color.White,
        backgroundTint = MaterialTheme.colorScheme.primary,
      )
      Spacer(modifier = Modifier.width(16.dp))
      Text(
        text = timeString,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onDelete) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "Delete Reminder",
          tint = Terracotta,
        )
      }
    }
  }
}

@Composable
fun AddReminderDialog(onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
  var hours by remember { mutableStateOf("") }
  var minutes by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Reminder") },
    text = {
      Column {
        Text("Notify me before cleaning:")
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = hours,
            onValueChange = { if (it.all { char -> char.isDigit() }) hours = it },
            label = { Text("Hours") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
          Spacer(modifier = Modifier.width(8.dp))
          OutlinedTextField(
            value = minutes,
            onValueChange = { if (it.all { char -> char.isDigit() }) minutes = it },
            label = { Text("Minutes") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val h = hours.toIntOrNull() ?: 0
          val m = minutes.toIntOrNull() ?: 0
          if (h > 0 || m > 0) {
            onConfirm(h, m)
          }
        }
      ) {
        Text("Add")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun WatchedStreetItem(spot: ParkingSpot) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = Color.White),
  ) {
    Row(
      modifier = Modifier.padding(16.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SquircleIcon(icon = Icons.Default.EditRoad, contentDescription = null, size = 48.dp)
      Column(modifier = Modifier.weight(1f)) {
        val title = spot.streetName ?: spot.neighborhood
        title?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        Row {
          spot.blockLimits?.let { limits ->
            Text(
              text = limits,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          spot.sweepingSide?.let { sweepingSide ->
            Text(
              text = " (${sweepingSide.name})",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        if (spot.sweepingSchedules.isNotEmpty()) {
          Text(
            text = spot.sweepingSchedules.joinToString(" | ") { it.formatSchedule() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
