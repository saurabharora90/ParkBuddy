package dev.parkbuddy.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun PermissionRationaleDialog(
  title: String,
  text: String,
  confirmButtonText: String = "OK",
  dismissButtonText: String = "Cancel",
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = title) },
    text = { Text(text = text) },
    confirmButton = { Button(onClick = onConfirm) { Text(text = confirmButtonText) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(text = dismissButtonText) } },
  )
}
