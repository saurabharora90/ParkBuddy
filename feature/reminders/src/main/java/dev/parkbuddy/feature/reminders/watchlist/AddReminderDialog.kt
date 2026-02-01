package dev.parkbuddy.feature.reminders.watchlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun AddReminderDialog(onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
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
