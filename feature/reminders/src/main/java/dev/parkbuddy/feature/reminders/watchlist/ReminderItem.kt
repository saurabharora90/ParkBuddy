package dev.parkbuddy.feature.reminders.watchlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.parkbuddy.core.ui.SquircleIcon

@Composable
internal fun ReminderItem(minutes: Int, onDelete: () -> Unit) {
  var isShowingConfirmationPrompt by remember { mutableStateOf(false) }

  val hours = minutes / 60
  val mins = minutes % 60
  val timeString =
    if (mins == 0) "$hours hr before"
    else if (hours == 0) "$mins min before" else "$hours hr $mins min before"

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
      IconButton(onClick = { isShowingConfirmationPrompt = true }) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "Delete Reminder",
          tint = Terracotta,
        )
      }
    }
  }

  if (isShowingConfirmationPrompt) {
    AlertDialog(
      onDismissRequest = { isShowingConfirmationPrompt = false },
      title = { Text(text = "Remove this reminder?") },
      text = {
        Text(
          "Are you sure you want to remove this reminder for future? " +
            "Existing reminder for an ongoing parking (if any) will not be altered"
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            isShowingConfirmationPrompt = false
            onDelete()
          }
        ) {
          Text("Yes")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { isShowingConfirmationPrompt = false },
          colors = ButtonDefaults.textButtonColors(contentColor = Terracotta),
        ) {
          Text("No")
        }
      },
    )
  }
}
