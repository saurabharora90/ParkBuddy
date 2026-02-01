package dev.parkbuddy.feature.reminders.watchlist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun PermissionRationaleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Enable Notifications & Alarms") },
    text = {
      Text(
        "To provide timely street cleaning reminders, ParkBuddy needs permission to send you notifications and schedule precise alarms. This ensures you never miss a cleaning window and avoid potential tickets."
      )
    },
    confirmButton = { Button(onClick = onConfirm) { Text("Enable") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Not Now") } },
  )
}
