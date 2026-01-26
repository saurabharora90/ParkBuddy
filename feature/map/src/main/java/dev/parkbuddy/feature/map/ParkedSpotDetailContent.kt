package dev.parkbuddy.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.parkbuddy.core.ui.ParkBuddyButton
import dev.parkbuddy.core.ui.SquircleIcon
import kotlin.time.Clock
import kotlin.time.Duration

@Composable
internal fun ParkedSpotDetailContent(
  spot: ParkingSpot,
  reminders: List<Int>,
  onMovedCar: () -> Unit,
  onEndSession: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .background(MaterialTheme.colorScheme.background)
      .padding(horizontal = 16.dp, vertical = 24.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    val now = Clock.System.now()
    val nextSweepingSchedule =
      spot.sweepingSchedules.sortedBy { it.nextOccurrence(now) }.firstOrNull()
    val nextCleaning = nextSweepingSchedule?.nextOccurrence(now)
    val hoursUntilCleaning = nextCleaning?.let { (it - now).inWholeHours } ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      spot.streetName?.let { streetName ->
        Text(
          text = streetName,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
      }
        ?: spot.neighborhood?.let { neighborhood ->
          Text(
            text = neighborhood,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
          )
        }

      Text(
        text = "● Safe for $hoursUntilCleaning more hours",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    nextSweepingSchedule?.let { schedule ->
      val nextCleaning = schedule.nextOccurrence(now)

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.White),
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            SquircleIcon(
              icon = Icons.Default.CleaningServices,
              size = 48.dp,
              shape = RoundedCornerShape(12.dp),
              iconTint = MaterialTheme.colorScheme.primary,
              backgroundTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            )

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Next Street Cleaning",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
              )
              Text(
                text = schedule.formatSchedule(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
              )
            }
          }

          nextCleaning?.let { nextTime ->
            val duration = nextTime - now
            val hoursUntil = duration.inWholeHours
            val minutesUntil = duration.inWholeMinutes % 60
            val secondsUntil = duration.inWholeSeconds % 60
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              CountDownText(
                hoursUntil.toInt(),
                "HOURS",
                modifier = Modifier.weight(1f),
              )

              CountDownText(
                minutesUntil.toInt(),
                "MINS",
                modifier = Modifier.weight(1f),
              )

              CountDownText(
                secondsUntil.toInt(),
                "SECS",
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }

    if (reminders.isNotEmpty() && nextCleaning != null) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "UPCOMING ALERTS",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
          )
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Terracotta.copy(alpha = 0.15f),
          ) {
            Text(
              text = "${reminders.size} ACTIVE",
              style = MaterialTheme.typography.labelSmall,
              color = Terracotta,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }

        reminders.take(2).forEachIndexed { index, reminderMinutes ->
          val alertTime = nextCleaning - Duration.parse("${reminderMinutes}m")
          val timeUntilAlert = alertTime - now
          val hoursUntilAlert = timeUntilAlert.inWholeHours

          val timeLabel = when {
            hoursUntilAlert < 12 -> "TONIGHT"
            hoursUntilAlert < 24 -> "TOMORROW"
            else -> "UPCOMING"
          }

          AlertCard(
            title = "Alert ${index + 1}: Move Reminder",
            subtitle = "(${reminderMinutes / 60}h before cleaning)",
            timeLabel = timeLabel,
          )
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      ParkBuddyButton(
        label = "MOVED",
        onClick = onMovedCar,
        icon = Icons.Default.Check,
        containerColor = SagePrimary,
        modifier = Modifier.weight(1f).heightIn(max = 52.dp),
      )

      ParkBuddyButton(
        label = "WRONG ?",
        onClick = onEndSession,
        icon = Icons.Default.NotificationsOff,
        containerColor = Terracotta,
        modifier = Modifier.weight(1f).heightIn(max = 52.dp),
      )
    }
  }
}

@Composable
private fun CountDownText(timeLeft: Int, unit: String, modifier: Modifier = Modifier) {
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier,
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(80.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(color = MaterialTheme.colorScheme.primaryContainer.copy(0.2f)),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = timeLeft.toString(),
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
      )
    }
    Text(
      text = unit,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = FontWeight.Medium,
    )
  }
}

@Composable
private fun AlertCard(title: String, subtitle: String, timeLabel: String) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SquircleIcon(
        icon = Icons.Default.NotificationsActive,
        size = 48.dp,
        iconTint = Terracotta,
        backgroundTint = Terracotta.copy(alpha = 0.15f)
      )

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Text(
        text = timeLabel,
        style = MaterialTheme.typography.labelMedium,
        color = Terracotta,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun ParkedSpotDetailContentPreview() {
  ParkBuddyTheme {
    ParkedSpotDetailContent(
      spot = spot,
      reminders = listOf(12),
      onMovedCar = {},
      onEndSession = {},
    )
  }
}
