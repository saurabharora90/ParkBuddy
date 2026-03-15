package dev.parkbuddy.feature.reminders.permitzone

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.parkbuddy.core.ui.ParkBuddyAlertDialog

@Composable
actual fun PermitZoneScreen(modifier: Modifier, viewModel: PermitZoneViewModel) {
  val context = LocalContext.current
  val availableZones by viewModel.availableZones.collectAsState()
  val selectedZone by viewModel.selectedZone.collectAsState()
  val permitSpotCount by viewModel.permitSpotCount.collectAsState()
  val permitSpots by viewModel.permitSpots.collectAsState()
  val reminders by viewModel.reminders.collectAsState()
  val isZonePickerExpanded by viewModel.isZonePickerExpanded.collectAsState()

  var showPermissionRationale by remember { mutableStateOf(false) }
  var lastKnownZoneWasNull by remember { mutableStateOf(selectedZone == null) }

  LaunchedEffect(selectedZone) {
    if (selectedZone != null && lastKnownZoneWasNull) {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      val needsAlarmPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          !alarmManager.canScheduleExactAlarms()
        } else {
          false
        }

      if (needsAlarmPermission) {
        showPermissionRationale = true
      }
    }
    lastKnownZoneWasNull = selectedZone == null
  }

  if (showPermissionRationale) {
    ParkBuddyAlertDialog(
      title = "One quick thing",
      text =
        "Your phone sometimes delays notifications to save battery. " +
          "Toggle on \"Allow setting alarms and reminders\" on the next screen " +
          "so your street cleaning reminders arrive exactly on time.",
      confirmLabel = "Open Settings",
      dismissLabel = null,
      onConfirm = {
        showPermissionRationale = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
          if (!alarmManager.canScheduleExactAlarms()) {
            context.startActivity(
              Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
              }
            )
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
    onZoneSelect = viewModel::selectZone,
    onAddReminder = viewModel::addReminder,
    onRemoveReminder = viewModel::removeReminder,
    modifier = modifier,
  )
}
