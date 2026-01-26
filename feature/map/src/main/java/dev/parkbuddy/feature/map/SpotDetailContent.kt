package dev.parkbuddy.feature.map

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.ParkingRegulation
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.bongballe.parkbuddy.model.Weekday
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.bongballe.parkbuddy.theme.SageGreen
import dev.parkbuddy.core.ui.ParkBuddyButton
import dev.parkbuddy.core.ui.SquircleIcon
import kotlin.time.Clock
import kotlinx.datetime.LocalTime

@Composable
internal fun SpotDetailContent(
  spot: ParkingSpot,
  isWatched: Boolean,
  onParkHere: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.background(MaterialTheme.colorScheme.background).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        spot.streetName?.let { streetName ->
          Text(text = streetName, style = MaterialTheme.typography.headlineSmall)
        }
          ?: spot.neighborhood?.let { neighborhood ->
            Text(text = neighborhood, style = MaterialTheme.typography.headlineSmall)
          }

        Row {
          spot.blockLimits?.let { limits ->
            Text(
              text = limits,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          spot.sweepingSide?.let { sweepingSide ->
            Text(
              text = " (${sweepingSide.name})",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      spot.rppArea?.let { zone ->
        Text(
          text = "Zone $zone",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier =
            Modifier.background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(0.5f),
                shape = MaterialTheme.shapes.medium,
              )
              .padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
    }

    val now = Clock.System.now()
    spot.sweepingSchedules.forEach { schedule ->
      val nextCleaning = schedule.nextOccurrence(now)

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.White),
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          SquircleIcon(
            icon = Icons.Default.CleaningServices,
            size = 48.dp,
            shape = CircleShape,
            iconTint = MaterialTheme.colorScheme.primary,
            backgroundTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
          )

          Column(modifier = Modifier.weight(1f)) {
            Text(text = "Street Cleaning", style = MaterialTheme.typography.titleMedium)
            Text(text = schedule.formatSchedule(), style = MaterialTheme.typography.bodyMedium)
          }

          nextCleaning?.let { nextTime ->
            val duration = nextTime - now
            val hoursUntil = duration.inWholeHours
            val timeUntilText =
              when {
                hoursUntil < 1 -> "in ${duration.inWholeMinutes} min"
                hoursUntil < 24 -> "in $hoursUntil hrs"
                else -> "in ${hoursUntil / 24} days"
              }
            Text(
              text = timeUntilText,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }

    if (isWatched) {

      ParkBuddyButton(label = "Park Here", onClick = onParkHere, modifier = Modifier.fillMaxWidth())

      Text(
        text = "This street is in your watched zone",
        style = MaterialTheme.typography.labelSmall,
        color = SageGreen,
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
    }
  }
}

@VisibleForTesting
private val spot =
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

@Preview(showBackground = true)
@Composable
private fun WatchedSpotDetailContentPreview() {
  ParkBuddyTheme { SpotDetailContent(spot = spot, isWatched = true, onParkHere = {}) }
}

@Preview(showBackground = true)
@Composable
private fun NonWatchedSpotDetailContentPreview() {
  ParkBuddyTheme { SpotDetailContent(spot = spot, isWatched = false, onParkHere = {}) }
}
