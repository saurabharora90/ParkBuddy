package dev.parkbuddy.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString

@Composable
fun ParkBuddyAlertDialog(
  title: String,
  text: String,
  confirmLabel: String,
  dismissLabel: String?,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  ParkBuddyAlertDialog(
    title = title,
    annotatedText = AnnotatedString(text),
    confirmLabel = confirmLabel,
    dismissLabel = dismissLabel,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
  )
}

@Composable
fun ParkBuddyAlertDialog(
  title: String?,
  annotatedText: AnnotatedString,
  confirmLabel: String,
  dismissLabel: String?,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = title?.let { { Text(title) } },
    text = { Text(annotatedText) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
    dismissButton =
      dismissLabel?.let {
        {
          TextButton(
            onClick = onDismiss,
            colors =
              ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
              ),
          ) {
            Text(dismissLabel)
          }
        }
      },
  )
}
