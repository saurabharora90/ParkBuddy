package dev.parkbuddy.feature.reminders.permitzone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun ZoneSelectorCard(
  availableZones: List<String>,
  selectedZone: String?,
  permitSpotCount: Int,
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
          text = "$permitSpotCount streets in your permit zone",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
    }
  }
}
