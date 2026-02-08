package dev.parkbuddy.feature.reminders.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditRoad
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.bongballe.parkbuddy.data.repository.utils.formatSchedule
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.parkbuddy.core.ui.SquircleIcon

@Composable
internal fun WatchedStreetItem(spot: ParkingSpot) {
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
