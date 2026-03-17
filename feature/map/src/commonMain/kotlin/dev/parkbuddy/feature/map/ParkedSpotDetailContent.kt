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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import dev.bongballe.parkbuddy.core.navigation.Navigator
import dev.bongballe.parkbuddy.data.repository.utils.formatSchedule
import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ReminderMinutes
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.parkbuddy.core.ui.ParkBuddyAlertDialog
import dev.parkbuddy.core.ui.ParkBuddyButton
import dev.parkbuddy.core.ui.ParkBuddyIcons
import dev.parkbuddy.core.ui.SquircleIcon
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Composable
internal fun ParkedSpotDetailContent(
  viewModel: ParkedSpotDetailViewModel,
  navigator: Navigator,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.stateFlow.collectAsState()
  ParkedSpotDetailContent(
    state = state,
    onMovedCar = {
      viewModel.markCarMoved()
      navigator.goBack()
    },
    onEndSession = {
      viewModel.reportWrongLocation()
      navigator.goBack()
    },
    modifier = modifier,
  )
}

@Composable
internal fun ParkedSpotDetailContent(
  state: ParkedSpotDetailState,
  onMovedCar: () -> Unit,
  onEndSession: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var isShowingConfirmCarMovedPrompt by remember { mutableStateOf(false) }
  var isShowingClearParkedLocationPrompt by remember { mutableStateOf(false) }

  Column(
    modifier =
      modifier
        .background(MaterialTheme.colorScheme.background)
        .padding(horizontal = 16.dp)
        .padding(bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    val now = state.now
    val restriction = state.restrictionState

    Header(state.spot, restriction, now)

    PrimaryCountdown(state.spot, restriction, now)

    SecondaryCleaningInfo(restriction, now)

    AlertsSection(restriction, state.reminders, now)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      ParkBuddyButton(
        label = "MOVED",
        onClick = { isShowingConfirmCarMovedPrompt = true },
        icon = ParkBuddyIcons.Check,
        containerColor = SagePrimary,
        modifier = Modifier.weight(1f).heightIn(max = 52.dp),
      )

      ParkBuddyButton(
        label = "WRONG ?",
        onClick = { isShowingClearParkedLocationPrompt = true },
        icon = ParkBuddyIcons.NotificationsOff,
        containerColor = Terracotta,
        modifier = Modifier.weight(1f).heightIn(max = 52.dp),
      )
    }
  }

  if (isShowingConfirmCarMovedPrompt) {
    ParkBuddyAlertDialog(
      title = "Are you sure?",
      text = "Marking your car as moved will clear your parked location and cancel the reminders.",
      confirmLabel = "Yes",
      dismissLabel = "No",
      onConfirm = {
        isShowingConfirmCarMovedPrompt = false
        onMovedCar()
      },
      onDismiss = { isShowingConfirmCarMovedPrompt = false },
    )
  }

  if (isShowingClearParkedLocationPrompt) {
    ParkBuddyAlertDialog(
      title = "Are you sure?",
      text =
        "We are sorry for detecting the wrong location. " +
          "Proceeding will clear this as parked location and cancel the reminders.",
      confirmLabel = "Yes",
      dismissLabel = "No",
      onConfirm = {
        isShowingClearParkedLocationPrompt = false
        onEndSession()
      },
      onDismiss = { isShowingClearParkedLocationPrompt = false },
    )
  }
}

@Composable
private fun Header(spot: ParkingSpot, restriction: ParkingRestrictionState, now: Instant) {
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
      when (restriction) {
        is ParkingRestrictionState.CleaningActive -> "\u25CF STREET CLEANING IN PROGRESS"

        is ParkingRestrictionState.Forbidden -> "\u25CF DO NOT PARK HERE"

        is ParkingRestrictionState.ForbiddenUpcoming -> "\u25CF Restriction ahead"

        is ParkingRestrictionState.PermitSafe -> {
          restriction.nextCleaning?.let {
            val remaining = it - now
            if (remaining.isNegative()) "\u25CF Permit zone, no restrictions"
            else "\u25CF Permit zone, safe for ${formatDurationCompact(remaining)}"
          } ?: "\u25CF Permit zone, no restrictions"
        }

        is ParkingRestrictionState.ActiveTimed -> {
          val remaining = restriction.expiry - now
          val prefix =
            if (remaining.isNegative()) "Time limit EXPIRED"
            else "Move within ${formatDurationCompact(remaining)}"
          val suffix = if (restriction.paymentRequired) " (PAY METER)" else ""
          "\u25CF $prefix$suffix"
        }

        is ParkingRestrictionState.PendingTimed -> {
          val startsIn = restriction.startsAt - now
          val prefix =
            if (startsIn.isNegative()) "Enforcement starting now"
            else "Enforcement starts in ${formatDurationCompact(startsIn)}"
          val suffix = if (restriction.paymentRequired) " (PAY METER)" else ""
          "\u25CF $prefix$suffix"
        }

        is ParkingRestrictionState.Unrestricted -> {
          restriction.nextCleaning?.let {
            val remaining = it - now
            if (remaining.isNegative()) "\u25CF No restrictions"
            else "\u25CF Safe for ${formatDurationCompact(remaining)}"
          } ?: "\u25CF No restrictions"
        }
      }

    val statusColor =
      when (restriction) {
        is ParkingRestrictionState.CleaningActive -> Terracotta
        is ParkingRestrictionState.Forbidden -> Terracotta
        is ParkingRestrictionState.ActiveTimed -> {
          val remaining = restriction.expiry - now
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
  restriction: ParkingRestrictionState,
  now: Instant,
) {
  when (restriction) {
    is ParkingRestrictionState.CleaningActive -> {
      TimeLimitCountdownCard(
        label = "Street Cleaning Ends",
        targetTime = restriction.cleaningEnd,
        now = now,
        accentColor = Terracotta,
      )
    }

    is ParkingRestrictionState.ActiveTimed -> {
      TimeLimitCountdownCard(
        label = "Time Limit Expires",
        targetTime = restriction.expiry,
        now = now,
        accentColor = Terracotta,
      )
    }

    is ParkingRestrictionState.PendingTimed -> {
      TimeLimitCountdownCard(
        label = "Enforcement Starts",
        targetTime = restriction.startsAt,
        now = now,
        accentColor = MaterialTheme.colorScheme.primary,
      )
    }

    is ParkingRestrictionState.Forbidden -> {
      val reasonText = restriction.reason.displayText().uppercase()
      AlertCard(
        title = "FORBIDDEN: $reasonText",
        subtitle = "Move your car immediately to avoid a ticket or towing.",
        timeLabel = "ACTIVE",
      )
    }

    is ParkingRestrictionState.ForbiddenUpcoming,
    is ParkingRestrictionState.PermitSafe,
    is ParkingRestrictionState.Unrestricted -> {
      CleaningCountdownCard(spot, restriction.nextCleaning, now)
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
      val duration = targetTime - now

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        SquircleIcon(
          icon = ParkBuddyIcons.AccessTime,
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
          Text(
            text = if (duration.isNegative()) "EXPIRED" else formatDurationCompact(duration),
            style = MaterialTheme.typography.titleMedium,
            color = accentColor,
            fontWeight = FontWeight.Bold,
          )
        }
      }

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
    remember(spot) { spot.sweepingSchedules.sortedBy { it.nextOccurrence(now) }.firstOrNull() }

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
            icon = ParkBuddyIcons.CleaningServices,
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
private fun SecondaryCleaningInfo(restriction: ParkingRestrictionState, now: Instant) {
  val showSecondary =
    restriction is ParkingRestrictionState.ActiveTimed ||
      restriction is ParkingRestrictionState.PendingTimed ||
      restriction is ParkingRestrictionState.Forbidden
  if (!showSecondary) return

  val nextCleaning = restriction.nextCleaning ?: return

  val duration = nextCleaning - now
  if (duration.isNegative()) return
  val cleaningText = formatRelativeTime(duration)

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
        icon = ParkBuddyIcons.CleaningServices,
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
  restriction: ParkingRestrictionState,
  reminders: List<ReminderMinutes>,
  now: Instant,
) {
  when (restriction) {
    is ParkingRestrictionState.CleaningActive -> {
      CleaningActiveAlertsSection(restriction.cleaningEnd, now)
    }

    is ParkingRestrictionState.Forbidden -> {
      ForbiddenAlertsSection(restriction.reason.displayText())
    }

    is ParkingRestrictionState.ForbiddenUpcoming -> {}

    is ParkingRestrictionState.ActiveTimed -> {
      if (restriction.paymentRequired) PaymentRequiredAlertsSection()
      TimeLimitAlertsSection(restriction.expiry, now)
    }

    is ParkingRestrictionState.PendingTimed -> {
      if (restriction.paymentRequired) PaymentRequiredAlertsSection()
      TimeLimitAlertsSection(restriction.expiry, now)
    }

    is ParkingRestrictionState.Unrestricted,
    is ParkingRestrictionState.PermitSafe -> {
      CleaningAlertsSection(reminders, restriction.nextCleaning, now)
    }
  }
}

@Composable
private fun PaymentRequiredAlertsSection() {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = "IMPORTANT INFO",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.5.sp,
    )

    AlertCard(
      title = "PAY AT METER",
      subtitle = "Check signs for time limits and rates.",
      timeLabel = "INFO",
    )
  }
}

@Composable
private fun ForbiddenAlertsSection(reason: String) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = "URGENT WARNING",
      style = MaterialTheme.typography.labelLarge,
      color = Terracotta,
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.5.sp,
    )

    AlertCard(title = "DO NOT PARK HERE", subtitle = reason, timeLabel = "CRITICAL")
  }
}

@Composable
private fun CleaningActiveAlertsSection(cleaningEnd: Instant, now: Instant) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = "URGENT ALERTS",
      style = MaterialTheme.typography.labelLarge,
      color = Terracotta,
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.5.sp,
    )

    AlertCard(
      title = "MOVE YOUR CAR NOW",
      subtitle = "Street cleaning is in progress!",
      timeLabel = if (cleaningEnd > now) "ACTIVE" else "ENDED",
    )
  }
}

@Composable
private fun TimeLimitAlertsSection(expiry: Instant, now: Instant) {
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

    val warningTimeLabel = timeLabelFor(warningTime - now)
    AlertCard(
      title = "Alert 1: Move Reminder",
      subtitle = "(15 min before time limit)",
      timeLabel = warningTimeLabel,
    )

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
      val alertTime = nextCleaning - reminder.value.minutes
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
      val alertTime = nextCleaning - reminder.value.minutes
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
        icon = ParkBuddyIcons.NotificationsActive,
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
private fun ParkedSpotDetailContentMeteredTimedPreview() {
  ParkBuddyTheme {
    ParkedSpotDetailContent(
      state =
        ParkedSpotDetailState(
          spot = previewSpot,
          restrictionState =
            ParkingRestrictionState.ActiveTimed(
              expiry = Clock.System.now() + 1.hours,
              paymentRequired = true,
              nextCleaning = Clock.System.now() + 24.hours,
            ),
          now = Clock.System.now(),
          reminders = emptyList(),
        ),
      onMovedCar = {},
      onEndSession = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun ParkedSpotDetailContentForbiddenPreview() {
  ParkBuddyTheme {
    ParkedSpotDetailContent(
      state =
        ParkedSpotDetailState(
          spot = previewSpot,
          restrictionState =
            ParkingRestrictionState.Forbidden(
              reason = dev.bongballe.parkbuddy.model.ProhibitionReason.NO_PARKING,
              nextCleaning = Clock.System.now() + 24.hours,
            ),
          now = Clock.System.now(),
          reminders = emptyList(),
        ),
      onMovedCar = {},
      onEndSession = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun ParkedSpotDetailContentCleaningActivePreview() {
  ParkBuddyTheme {
    ParkedSpotDetailContent(
      state =
        ParkedSpotDetailState(
          spot = previewSpot,
          restrictionState =
            ParkingRestrictionState.CleaningActive(
              cleaningEnd = Clock.System.now() + 45.minutes,
              nextCleaning = Clock.System.now() + (24 * 7).hours,
            ),
          now = Clock.System.now(),
          reminders = emptyList(),
        ),
      onMovedCar = {},
      onEndSession = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun ParkedSpotDetailContentUnrestrictedPreview() {
  ParkBuddyTheme {
    ParkedSpotDetailContent(
      state =
        ParkedSpotDetailState(
          spot = previewSpot,
          restrictionState =
            ParkingRestrictionState.Unrestricted(nextCleaning = Clock.System.now() + 48.hours),
          now = Clock.System.now(),
          reminders = listOf(ReminderMinutes(720)),
        ),
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
      state =
        ParkedSpotDetailState(
          spot = previewSpot,
          restrictionState =
            ParkingRestrictionState.ActiveTimed(
              expiry = Clock.System.now() + 1.hours + 30.minutes,
              paymentRequired = false,
              nextCleaning = Clock.System.now() + 48.hours,
            ),
          now = Clock.System.now(),
          reminders = emptyList(),
        ),
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
      state =
        ParkedSpotDetailState(
          spot = previewSpot,
          restrictionState =
            ParkingRestrictionState.PendingTimed(
              startsAt = Clock.System.now() + 3.hours,
              expiry = Clock.System.now() + 5.hours,
              paymentRequired = false,
              nextCleaning = null,
            ),
          now = Clock.System.now(),
          reminders = emptyList(),
        ),
      onMovedCar = {},
      onEndSession = {},
    )
  }
}
