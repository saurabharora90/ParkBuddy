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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bongballe.parkbuddy.data.repository.utils.formatSchedule
import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ReminderMinutes
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.parkbuddy.core.ui.ParkBuddyButton
import dev.parkbuddy.core.ui.SquircleIcon
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.delay

@Composable
internal fun ParkedSpotDetailContent(
  spot: ParkingSpot,
  restrictionState: ParkingRestrictionState,
  reminders: List<ReminderMinutes>,
  onMovedCar: () -> Unit,
  onEndSession: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var currentTime by remember { mutableStateOf(Clock.System.now()) }

  LaunchedEffect(Unit) {
    while (true) {
      delay(1000)
      currentTime = Clock.System.now()
    }
  }

  Column(
    modifier =
      modifier
        .background(MaterialTheme.colorScheme.background)
        .padding(horizontal = 16.dp)
        .padding(bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    val now = currentTime

    // Header: street name + status
    Header(spot, restrictionState, now)

    // Primary countdown card
    PrimaryCountdown(spot, restrictionState, now)

    // Secondary cleaning info for timed states
    SecondaryCleaningInfo(restrictionState, now)

    // Alerts section
    AlertsSection(restrictionState, reminders, now)

    // Action buttons
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
private fun Header(spot: ParkingSpot, restrictionState: ParkingRestrictionState, now: Instant) {
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

    val statusText =
      when (restrictionState) {
        is ParkingRestrictionState.Unrestricted -> {
          restrictionState.nextCleaning?.let {
            val hours = (it - now).inWholeHours
            "● Safe for $hours more hours"
          } ?: "● No restrictions"
        }
        is ParkingRestrictionState.PermitSafe -> {
          restrictionState.nextCleaning?.let {
            val hours = (it - now).inWholeHours
            "● Permit zone, safe for $hours more hours"
          } ?: "● Permit zone, no restrictions"
        }
        is ParkingRestrictionState.ActiveTimed -> {
          val remaining = restrictionState.expiry - now
          if (remaining.isNegative()) "● Time limit EXPIRED"
          else "● Move within ${formatDurationCompact(remaining)}"
        }
        is ParkingRestrictionState.PendingTimed -> {
          val startsIn = restrictionState.startsAt - now
          if (startsIn.isNegative()) "● Enforcement starting now"
          else "● Enforcement starts in ${formatDurationCompact(startsIn)}"
        }
      }

    val statusColor =
      when (restrictionState) {
        is ParkingRestrictionState.ActiveTimed -> {
          val remaining = restrictionState.expiry - now
          if (remaining.isNegative() || remaining.inWholeMinutes < 30) Terracotta
          else MaterialTheme.colorScheme.onSurfaceVariant
        }
        else -> MaterialTheme.colorScheme.onSurfaceVariant
      }

    Text(text = statusText, style = MaterialTheme.typography.titleSmall, color = statusColor)
  }
}

@Composable
private fun PrimaryCountdown(
  spot: ParkingSpot,
  restrictionState: ParkingRestrictionState,
  now: Instant,
) {
  when (restrictionState) {
    is ParkingRestrictionState.ActiveTimed -> {
      TimeLimitCountdownCard(
        label = "Time Limit Expires",
        targetTime = restrictionState.expiry,
        now = now,
        accentColor = Terracotta,
      )
    }
    is ParkingRestrictionState.PendingTimed -> {
      TimeLimitCountdownCard(
        label = "Enforcement Starts",
        targetTime = restrictionState.startsAt,
        now = now,
        accentColor = MaterialTheme.colorScheme.primary,
      )
    }
    is ParkingRestrictionState.Unrestricted,
    is ParkingRestrictionState.PermitSafe -> {
      CleaningCountdownCard(spot, restrictionState.nextCleaning, now)
    }
  }
}

@Composable
private fun TimeLimitCountdownCard(
  label: String,
  targetTime: Instant,
  now: Instant,
  accentColor: Color,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = Color.White),
  ) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        SquircleIcon(
          icon = Icons.Default.AccessTime,
          size = 48.dp,
          shape = RoundedCornerShape(12.dp),
          iconTint = accentColor,
          backgroundTint = accentColor.copy(alpha = 0.15f),
        )

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
          )
          val duration = targetTime - now
          Text(
            text = if (duration.isNegative()) "EXPIRED" else formatDurationCompact(duration),
            style = MaterialTheme.typography.titleMedium,
            color = accentColor,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      val duration = targetTime - now
      if (!duration.isNegative()) {
        val hoursUntil = duration.inWholeHours
        val minutesUntil = duration.inWholeMinutes % 60
        val secondsUntil = duration.inWholeSeconds % 60
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          CountDownText(hoursUntil.toInt(), "HOURS", modifier = Modifier.weight(1f))
          CountDownText(minutesUntil.toInt(), "MINS", modifier = Modifier.weight(1f))
          CountDownText(secondsUntil.toInt(), "SECS", modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun CleaningCountdownCard(spot: ParkingSpot, nextCleaning: Instant?, now: Instant) {
  val nextSweepingSchedule =
    spot.sweepingSchedules.sortedBy { it.nextOccurrence(now) }.firstOrNull()

  nextSweepingSchedule?.let { schedule ->
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
          if (!duration.isNegative()) {
            val hoursUntil = duration.inWholeHours
            val minutesUntil = duration.inWholeMinutes % 60
            val secondsUntil = duration.inWholeSeconds % 60
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              CountDownText(hoursUntil.toInt(), "HOURS", modifier = Modifier.weight(1f))
              CountDownText(minutesUntil.toInt(), "MINS", modifier = Modifier.weight(1f))
              CountDownText(secondsUntil.toInt(), "SECS", modifier = Modifier.weight(1f))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SecondaryCleaningInfo(restrictionState: ParkingRestrictionState, now: Instant) {
  val nextCleaning =
    when (restrictionState) {
      is ParkingRestrictionState.ActiveTimed -> restrictionState.nextCleaning
      is ParkingRestrictionState.PendingTimed -> restrictionState.nextCleaning
      else -> return
    } ?: return

  val duration = nextCleaning - now
  if (duration.isNegative()) return
  val cleaningText =
    when {
      duration.inWholeHours < 1 -> "in ${duration.inWholeMinutes} min"
      duration.inWholeHours < 24 -> "in ${duration.inWholeHours} hrs"
      else -> "in ${duration.inWholeHours / 24} days"
    }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SquircleIcon(
        icon = Icons.Default.CleaningServices,
        size = 40.dp,
        iconTint = MaterialTheme.colorScheme.primary,
        backgroundTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
      )

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Street Cleaning",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = cleaningText,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun AlertsSection(
  restrictionState: ParkingRestrictionState,
  reminders: List<ReminderMinutes>,
  now: Instant,
) {
  when (restrictionState) {
    is ParkingRestrictionState.ActiveTimed,
    is ParkingRestrictionState.PendingTimed -> {
      TimeLimitAlertsSection(restrictionState, now)
    }
    is ParkingRestrictionState.Unrestricted,
    is ParkingRestrictionState.PermitSafe -> {
      CleaningAlertsSection(reminders, restrictionState.nextCleaning, now)
    }
  }
}

@Composable
private fun TimeLimitAlertsSection(restrictionState: ParkingRestrictionState, now: Instant) {
  val expiry =
    when (restrictionState) {
      is ParkingRestrictionState.ActiveTimed -> restrictionState.expiry
      is ParkingRestrictionState.PendingTimed -> restrictionState.expiry
      else -> return
    }

  val warningTime = expiry - 15.minutes
  val activeCount = listOf(warningTime, expiry).count { it > now }

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
      if (activeCount > 0) {
        Surface(shape = RoundedCornerShape(12.dp), color = Terracotta.copy(alpha = 0.15f)) {
          Text(
            text = "$activeCount ACTIVE",
            style = MaterialTheme.typography.labelSmall,
            color = Terracotta,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }
    }

    // 15-minute warning
    val warningTimeLabel = timeLabelFor(warningTime - now)
    AlertCard(
      title = "Alert 1: Move Reminder",
      subtitle = "(15 min before time limit)",
      timeLabel = warningTimeLabel,
    )

    // At expiry
    val expiryTimeLabel = timeLabelFor(expiry - now)
    AlertCard(
      title = "Alert 2: Time Limit Expired",
      subtitle = "(at expiry)",
      timeLabel = expiryTimeLabel,
    )
  }
}

@Composable
private fun CleaningAlertsSection(
  reminders: List<ReminderMinutes>,
  nextCleaning: Instant?,
  now: Instant,
) {
  if (reminders.isEmpty() || nextCleaning == null) return

  val displayedReminders = reminders.take(2)
  val activeCount =
    displayedReminders.count { reminder ->
      val alertTime = nextCleaning - Duration.parse("${reminder.value}m")
      alertTime > now
    }

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
      if (activeCount > 0) {
        Surface(shape = RoundedCornerShape(12.dp), color = Terracotta.copy(alpha = 0.15f)) {
          Text(
            text = "$activeCount ACTIVE",
            style = MaterialTheme.typography.labelSmall,
            color = Terracotta,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }
    }

    displayedReminders.forEachIndexed { index, reminder ->
      val alertTime = nextCleaning - Duration.parse("${reminder.value}m")
      val timeLabel = timeLabelFor(alertTime - now)

      AlertCard(
        title = "Alert ${index + 1}: Move Reminder",
        subtitle = "(${reminder.value / 60}h before cleaning)",
        timeLabel = timeLabel,
      )
    }
  }
}

private fun timeLabelFor(timeUntil: Duration): String {
  val hoursUntil = timeUntil.inWholeHours
  return when {
    timeUntil.isNegative() -> "PASSED"
    hoursUntil < 12 -> "TONIGHT"
    hoursUntil < 24 -> "TOMORROW"
    else -> "UPCOMING"
  }
}

private fun formatDurationCompact(duration: Duration): String {
  val hours = duration.inWholeHours
  val minutes = duration.inWholeMinutes % 60
  return when {
    hours > 0 -> "${hours}h ${minutes}m"
    else -> "${minutes}m"
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
        Modifier.fillMaxWidth()
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
        backgroundTint = Terracotta.copy(alpha = 0.15f),
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
private fun ParkedSpotDetailContentUnrestrictedPreview() {
  ParkBuddyTheme {
    ParkedSpotDetailContent(
      spot = spot,
      restrictionState =
        ParkingRestrictionState.Unrestricted(nextCleaning = Clock.System.now() + 48.hours),
      reminders = listOf(ReminderMinutes(720)),
      onMovedCar = {},
      onEndSession = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun ParkedSpotDetailContentActiveTimedPreview() {
  ParkBuddyTheme {
    ParkedSpotDetailContent(
      spot = spot,
      restrictionState =
        ParkingRestrictionState.ActiveTimed(
          expiry = Clock.System.now() + 1.hours + 30.minutes,
          nextCleaning = Clock.System.now() + 48.hours,
        ),
      reminders = emptyList(),
      onMovedCar = {},
      onEndSession = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun ParkedSpotDetailContentPendingTimedPreview() {
  ParkBuddyTheme {
    ParkedSpotDetailContent(
      spot = spot,
      restrictionState =
        ParkingRestrictionState.PendingTimed(
          startsAt = Clock.System.now() + 3.hours,
          expiry = Clock.System.now() + 5.hours,
          nextCleaning = null,
        ),
      reminders = emptyList(),
      onMovedCar = {},
      onEndSession = {},
    )
  }
}
