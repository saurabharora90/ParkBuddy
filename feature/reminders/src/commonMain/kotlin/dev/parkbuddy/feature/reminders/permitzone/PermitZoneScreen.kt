package dev.parkbuddy.feature.reminders.permitzone

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ReminderMinutes
import dev.parkbuddy.core.ui.NestedScaffold
import dev.parkbuddy.core.ui.ParkBuddyIcons
import dev.parkbuddy.core.ui.SquircleIcon
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
expect fun PermitZoneScreen(
  modifier: Modifier = Modifier,
  viewModel: PermitZoneViewModel = metroViewModel(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PermitZoneContent(
  availableZones: List<String>,
  selectedZone: String?,
  permitSpotCount: Int,
  permitSpots: List<ParkingSpot>,
  reminders: List<ReminderMinutes>,
  isZonePickerExpanded: Boolean,
  onZonePickerExpandedChange: (Boolean) -> Unit,
  onZoneSelect: (String?) -> Unit,
  onAddReminder: (Int, Int) -> Unit,
  onRemoveReminder: (ReminderMinutes) -> Unit,
  modifier: Modifier = Modifier,
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

  NestedScaffold(
    topBar = {
      TopAppBar(title = { Text(text = "Your Parking Zone", modifier = Modifier.fillMaxWidth()) })
    },
    containerColor = MaterialTheme.colorScheme.background,
    modifier = modifier,
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
          permitSpotCount = permitSpotCount,
          isExpanded = isZonePickerExpanded,
          onExpandedChange = onZonePickerExpandedChange,
          onZoneSelect = onZoneSelect,
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
                Icon(ParkBuddyIcons.Add, contentDescription = "Add Reminder")
              }
            }
          }
        }

        if (reminders.isEmpty()) {
          item { Text("No reminders set.", style = MaterialTheme.typography.bodyMedium) }
        } else {
          items(reminders) { reminder ->
            ReminderItem(minutes = reminder.value, onDelete = { onRemoveReminder(reminder) })
          }
        }

        item {
          Text(
            "PERMIT STREETS",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
          )
        }

        items(permitSpots) { spot -> PermitStreetItem(spot = spot) }
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
                icon = ParkBuddyIcons.LocationCity,
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
                  "Live in a permit zone? Set it here so we know when you're exempt from local time limits. " +
                    "We'll handle all the streets and reminders for you.",
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
