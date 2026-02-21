package dev.parkbuddy.feature.reminders.permitzone

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.ParkingRegulation
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ReminderMinutes
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.bongballe.parkbuddy.model.Weekday
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.parkbuddy.core.ui.NestedScaffold
import dev.parkbuddy.core.ui.PermissionRationaleDialog
import dev.parkbuddy.core.ui.SquircleIcon
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermitZoneScreen(
  modifier: Modifier = Modifier,
  viewModel: PermitZoneViewModel = metroViewModel(),
) {
  val context = LocalContext.current
  val availableZones by viewModel.availableZones.collectAsState()
  val selectedZone by viewModel.selectedZone.collectAsState()
  val permitSpotCount by viewModel.permitSpotCount.collectAsState()
  val permitSpots by viewModel.permitSpots.collectAsState()
  val reminders by viewModel.reminders.collectAsState()
  val isZonePickerExpanded by viewModel.isZonePickerExpanded.collectAsState()

  var showPermissionRationale by remember { mutableStateOf(false) }
  var lastKnownZoneWasNull by remember { mutableStateOf(selectedZone == null) }

  val notificationPermissionState =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      null
    }

  LaunchedEffect(selectedZone) {
    if (selectedZone != null && lastKnownZoneWasNull) {
      val needsNotificationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          !notificationPermissionState!!.status.isGranted
        } else {
          false
        }

      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      val needsAlarmPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          !alarmManager.canScheduleExactAlarms()
        } else {
          false
        }

      if (needsNotificationPermission || needsAlarmPermission) {
        showPermissionRationale = true
      }
    }
    lastKnownZoneWasNull = selectedZone == null
  }

  if (showPermissionRationale) {
    PermissionRationaleDialog(
      title = "Enable Notifications & Alarms",
      text =
        "To provide timely street cleaning reminders, ParkBuddy needs permission to send " +
          "you notifications and schedule precise alarms. This ensures you never miss a " +
          "cleaning window and avoid potential tickets.",
      confirmButtonText = "Enable",
      dismissButtonText = "Not Now",
      onConfirm = {
        showPermissionRationale = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          notificationPermissionState?.launchPermissionRequest()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
          if (!alarmManager.canScheduleExactAlarms()) {
            val intent =
              Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
              }
            context.startActivity(intent)
          }
        }
      },
      onDismiss = { showPermissionRationale = false },
    )
  }

  PermitZoneContent(
    availableZones = availableZones,
    selectedZone = selectedZone,
    permitSpotCount = permitSpotCount,
    permitSpots = permitSpots,
    reminders = reminders,
    isZonePickerExpanded = isZonePickerExpanded,
    onZonePickerExpandedChange = viewModel::setZonePickerExpanded,
    onZoneSelected = viewModel::selectZone,
    onAddReminder = viewModel::addReminder,
    onRemoveReminder = viewModel::removeReminder,
    modifier = modifier,
  )
}

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
  onZoneSelected: (String?) -> Unit,
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
                  "Choose your residential parking permit zone to automatically " +
                    "manage all streets in your area and receive street cleaning reminders.",
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

@Preview(showBackground = true)
@Composable
private fun PermitZoneContentPreview() {
  val sampleSpot =
    ParkingSpot(
      objectId = "1",
      geometry = Geometry(type = "Line", coordinates = listOf(listOf(1.0, 2.0), listOf(3.0, 4.0))),
      streetName = "Market Street",
      blockLimits = "1st Ave - 2nd Ave",
      neighborhood = "Downtown",
      regulation = ParkingRegulation.TIME_LIMITED,
      rppArea = "A",
      timeLimitHours = 2,
      enforcementDays = "Mon-Fri",
      enforcementStart = LocalTime(8, 0),
      enforcementEnd = LocalTime(18, 0),
      sweepingCnn = "12345",
      sweepingSide = StreetSide.LEFT,
      sweepingSchedules =
        listOf(
          SweepingSchedule(
            weekday = Weekday.Mon,
            fromHour = 8,
            toHour = 10,
            week1 = true,
            week2 = true,
            week3 = true,
            week4 = true,
            week5 = true,
            holidays = false,
          )
        ),
    )

  ParkBuddyTheme {
    PermitZoneContent(
      availableZones = listOf("A", "B", "C"),
      selectedZone = "A",
      permitSpotCount = 5,
      permitSpots = listOf(sampleSpot),
      reminders = listOf(ReminderMinutes(60)),
      isZonePickerExpanded = false,
      onZonePickerExpandedChange = {},
      onZoneSelected = {},
      onAddReminder = { _, _ -> },
      onRemoveReminder = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun PermitZoneContentEmptyPreview() {
  ParkBuddyTheme {
    PermitZoneContent(
      availableZones = listOf("A", "B", "C"),
      selectedZone = null,
      permitSpotCount = 0,
      permitSpots = emptyList(),
      reminders = emptyList(),
      isZonePickerExpanded = false,
      onZonePickerExpandedChange = {},
      onZoneSelected = {},
      onAddReminder = { _, _ -> },
      onRemoveReminder = {},
    )
  }
}
