package dev.parkbuddy.feature.map

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bongballe.parkbuddy.data.repository.utils.DateTimeUtils
import dev.bongballe.parkbuddy.data.repository.utils.formatSchedule
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.IntervalSource
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.bongballe.parkbuddy.model.Weekday
import dev.bongballe.parkbuddy.theme.Goldenrod
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.bongballe.parkbuddy.theme.SageGreen
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.bongballe.parkbuddy.theme.WildIris
import dev.parkbuddy.core.ui.ParkBuddyButton
import dev.parkbuddy.core.ui.ParkBuddyIcons
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

@Composable
internal fun SpotDetailContent(
  spot: ParkingSpot,
  isInPermitZone: Boolean,
  onParkHere: () -> Unit,
  modifier: Modifier = Modifier,
  clock: Clock = Clock.System,
) {
  Column(
    modifier =
      modifier
        .background(MaterialTheme.colorScheme.background)
        .padding(bottom = 16.dp)
        .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    // Header: street name, block limits, side, RPP zone badge
    SpotHeader(spot)

    // Current state card: what rule is active RIGHT NOW
    CurrentStateCard(spot, isInPermitZone, clock)

    // "PAY AT METER" banner for spots with any metered intervals (so the user knows to bring
    // change)
    val hasMeteredIntervals = spot.timeline.any { it.type is IntervalType.Metered }
    if (hasMeteredIntervals && !isInPermitZone) {
      StatusBanner(icon = ParkBuddyIcons.Error, text = "PAY AT METER.", accentColor = Terracotta)
    }

    // Timeline section + sweeping schedules
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
      Column(
        modifier = Modifier.padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        // Timeline intervals (unified timed + meter rules)
        if (spot.timeline.isNotEmpty()) {
          val now = clock.now()

          Text(
            text = "PARKING RULES & SCHEDULES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
          )

          val sortedIntervals =
            spot.timeline.sortedWith(compareBy({ it.days.minOrNull() }, { it.startTime }))

          sortedIntervals.forEach { interval ->
            IntervalRow(interval = interval, isActive = interval.isActiveAt(now))
          }
        }

        // Sweeping schedules (separate because they carry week-of-month info)
        spot.sweepingSchedules.forEach { schedule -> NoParkingInfo(schedule, clock) }
      }
    }

    ParkBuddyButton(label = "Park Here", onClick = onParkHere, modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun SpotHeader(spot: ParkingSpot) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
      spot.streetName?.let { streetName ->
        Text(text = streetName, style = MaterialTheme.typography.titleLarge)
      }
        ?: spot.neighborhood?.let { neighborhood ->
          Text(text = neighborhood, style = MaterialTheme.typography.titleLarge)
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

    if (spot.rppAreas.isNotEmpty()) {
      Text(
        text =
          if (spot.rppAreas.size > 1) "Zones ${spot.rppAreas.joinToString(" or ")}"
          else "Zone ${spot.rppAreas.first()}",
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
}

/**
 * Shows the CURRENT parking status based on the active timeline interval.
 *
 * Color scheme per state:
 * - Free / Open: SagePrimary
 * - Permit exempt: SageGreen
 * - Limited: WildIris
 * - Metered: Goldenrod
 * - Restricted / Forbidden: Terracotta
 */
@Composable
private fun CurrentStateCard(spot: ParkingSpot, isInPermitZone: Boolean, clock: Clock) {
  val now = clock.now()

  // Check sweeping FIRST. Sweeping lives outside the timeline (week-of-month semantics)
  // but is the highest-priority restriction. Without this check, the card would show
  // "FREE PARKING" during active street cleaning.
  val activeSweeping = spot.sweepingSchedules.firstOrNull { it.isWithinWindow(now) }
  if (activeSweeping != null) {
    StatusBanner(
      icon = ParkBuddyIcons.Error,
      text = "NO PARKING: STREET CLEANING IN PROGRESS",
      accentColor = Terracotta,
    )
    return
  }

  val activeInterval = spot.timeline.firstOrNull { it.isActiveAt(now) }

  // Permit holders get a special "safe" banner if their zone is exempt
  if (isInPermitZone && activeInterval != null) {
    val userExempt = spot.rppAreas.any { it in activeInterval.exemptPermitZones }
    if (userExempt) {
      StatusBanner(
        icon = ParkBuddyIcons.SafetyCheck,
        text = buildPermitExemptText(spot, activeInterval),
        accentColor = SageGreen,
      )
      return
    }
  }

  // Only show permit-safe banner if the spot is actually parkable.
  // A NO_PARKING or GOVERNMENT_ONLY spot with an empty timeline should NOT show "FREE PARKING".
  if (isInPermitZone && activeInterval == null && spot.isParkable) {
    StatusBanner(
      icon = ParkBuddyIcons.SafetyCheck,
      text = "PERMIT ${spot.rppAreas.joinToString(" or ")} VALID. FREE PARKING.",
      accentColor = SageGreen,
    )
    return
  }

  when (val type = activeInterval?.type) {
    null,
    is IntervalType.Open -> {
      StatusBanner(
        icon = ParkBuddyIcons.CheckCircle,
        text = "FREE PARKING",
        accentColor = SagePrimary,
      )
    }

    is IntervalType.Limited -> {
      val limit = formatLimit(type.timeLimitMinutes)
      val endFormatted = formatTime(activeInterval.endTime)
      StatusBanner(
        icon = ParkBuddyIcons.AccessTime,
        text = "MAX $limit UNTIL $endFormatted",
        accentColor = WildIris,
      )
    }

    is IntervalType.Metered -> {
      val endFormatted = formatTime(activeInterval.endTime)
      val limitText =
        if (type.timeLimitMinutes > 0)
          "MAX ${formatLimit(type.timeLimitMinutes)} UNTIL $endFormatted"
        else "METERED UNTIL $endFormatted"
      StatusBanner(icon = ParkBuddyIcons.AccessTime, text = limitText, accentColor = Goldenrod)
    }

    is IntervalType.Restricted -> {
      StatusBanner(
        icon = ParkBuddyIcons.Error,
        text = type.reason.uppercase(),
        accentColor = Terracotta,
      )
    }

    is IntervalType.Forbidden -> {
      StatusBanner(
        icon = ParkBuddyIcons.Error,
        text = "NO PARKING: ${type.reason.uppercase()}",
        accentColor = Terracotta,
      )
    }
  }
}

/**
 * Builds the text for permit-exempt users. Shows what the general public rule is so the user
 * understands why others can't park there.
 */
private fun buildPermitExemptText(spot: ParkingSpot, interval: ParkingInterval): String {
  val zone = spot.rppAreas.joinToString(" or ")
  return when (val type = interval.type) {
    is IntervalType.Limited ->
      "FREE FOR YOU (PERMIT $zone). GENERAL: MAX ${formatLimit(type.timeLimitMinutes)}."
    is IntervalType.Metered -> "FREE FOR YOU (PERMIT $zone). GENERAL: PAY AT METER."
    else -> "PERMIT $zone VALID. TIME LIMITS DO NOT APPLY."
  }
}

/** Colored banner row used for the current state card. */
@Composable
private fun StatusBanner(
  icon: ImageVector,
  text: String,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    modifier =
      modifier
        .fillMaxWidth()
        .background(accentColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.large)
        .padding(16.dp),
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = accentColor)
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = accentColor)
  }
}

/**
 * A single row in the timeline section. Shows the interval type icon, day/time schedule, and any
 * time limit information.
 *
 * Color coding:
 * - LIMITED -> WildIris
 * - METERED -> Goldenrod
 * - RESTRICTED -> Terracotta
 * - FORBIDDEN -> Terracotta (darker via alpha)
 */
@Composable
private fun IntervalRow(
  interval: ParkingInterval,
  isActive: Boolean,
  modifier: Modifier = Modifier,
) {
  val tint = intervalColor(interval.type)
  val icon = intervalIcon(interval.type)
  val label = intervalLabel(interval)

  val backgroundColor =
    if (isActive)
      rememberInfiniteTransition(label = "intervalRowBg")
        .animateColor(
          initialValue = tint.copy(alpha = 0.05f),
          targetValue = tint.copy(alpha = 0.25f),
          animationSpec =
            infiniteRepeatable(
              animation = tween(durationMillis = 1000),
              repeatMode = RepeatMode.Reverse,
            ),
        )
    else remember { mutableStateOf(Color.Transparent) }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    modifier =
      modifier
        .fillMaxWidth()
        .background(backgroundColor.value)
        .padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = tint)

    val annotatedString = buildAnnotatedString {
      append(label)
      withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
        append(
          "${
            interval.days.sortedBy { it.ordinal }.joinToString(
              prefix = " ",
              transform = { it.name.substring(0, 3) },
            )
          },  ${formatTime(interval.startTime)}-${formatTime(interval.endTime)}"
        )
      }
    }

    Text(text = annotatedString, style = MaterialTheme.typography.bodyMedium)
  }
}

/** Returns the accent color for a given interval type. */
private fun intervalColor(type: IntervalType): Color =
  when (type) {
    is IntervalType.Open -> SagePrimary
    is IntervalType.Limited -> WildIris
    is IntervalType.Metered -> Goldenrod
    is IntervalType.Restricted -> Terracotta
    is IntervalType.Forbidden -> Terracotta
  }

/** Returns the appropriate icon for a given interval type. */
@Composable
private fun intervalIcon(type: IntervalType): ImageVector =
  when (type) {
    is IntervalType.Open -> ParkBuddyIcons.CheckCircle
    is IntervalType.Limited -> ParkBuddyIcons.AccessTime
    is IntervalType.Metered -> ParkBuddyIcons.AccessTime
    is IntervalType.Restricted -> ParkBuddyIcons.Warning
    is IntervalType.Forbidden -> ParkBuddyIcons.Error
  }

/** Builds the label prefix for a timeline row (e.g. "Max 2 hrs:", "TOW AWAY:", "METERED:"). */
private fun intervalLabel(interval: ParkingInterval): String =
  when (val type = interval.type) {
    is IntervalType.Open -> "Free:"
    is IntervalType.Limited -> "Max ${formatLimit(type.timeLimitMinutes)}:"
    is IntervalType.Metered -> {
      if (type.timeLimitMinutes > 0) "Max ${formatLimit(type.timeLimitMinutes)}:" else "METERED:"
    }
    is IntervalType.Restricted -> "${type.reason.uppercase()}:"
    is IntervalType.Forbidden -> "${type.reason.uppercase()}:"
  }

private fun formatLimit(minutes: Int): String =
  when {
    minutes == 0 -> ""
    minutes >= 60 -> {
      val hrs = minutes / 60.0
      if (hrs == hrs.toInt().toDouble()) "${hrs.toInt()} hrs" else "$hrs hrs"
    }
    else -> "$minutes min"
  }

/** Formats a [LocalTime] to a human-readable string like "8 AM" or "6 PM". */
private fun formatTime(time: LocalTime): String = DateTimeUtils.formatHour(time.hour)

@Composable
private fun NoParkingInfo(schedule: SweepingSchedule, clock: Clock, modifier: Modifier = Modifier) {
  val now = clock.now()
  val nextCleaning = schedule.nextOccurrence(now)
  val isActive = schedule.isWithinWindow(now)

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Icon(imageVector = ParkBuddyIcons.Error, contentDescription = null, tint = Terracotta)

    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      val noParkingTiming = buildAnnotatedString {
        append("No Parking: ")
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
          append(schedule.formatSchedule())
        }
      }
      Text(text = noParkingTiming, style = MaterialTheme.typography.bodyMedium)

      Row {
        Text(
          text = "STREET CLEANING",
          style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
          modifier = Modifier.background(Terracotta.copy(alpha = 0.2f)).padding(2.dp),
        )

        if (isActive) {
          Text(
            text = " • IN PROGRESS",
            style =
              MaterialTheme.typography.bodyMedium.copy(
                color = Terracotta,
                fontWeight = FontWeight.Bold,
              ),
          )
        } else {
          nextCleaning?.let { nextTime ->
            val duration = nextTime - now
            val hoursUntil = duration.inWholeHours
            val timeUntilText =
              when {
                hoursUntil < 1 -> "in ${duration.inWholeMinutes} min"
                hoursUntil < 24 -> "in $hoursUntil hrs"
                else -> "in ${hoursUntil / 24} days"
              }
            Text(text = " • $timeUntilText", style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    }
  }
}

// -- Preview data --

@VisibleForTesting
internal val spot =
  ParkingSpot(
    objectId = "1",
    geometry = Geometry(type = "Line", coordinates = listOf(listOf(1.0, 2.0), listOf(3.0, 4.0))),
    streetName = "Market Street",
    blockLimits = "1st Ave - 2nd Ave",
    neighborhood = "Downtown",
    rppAreas = listOf("A"),
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
    timeline =
      listOf(
        ParkingInterval(
          type = IntervalType.Limited(timeLimitMinutes = 120),
          days =
            setOf(
              DayOfWeek.MONDAY,
              DayOfWeek.TUESDAY,
              DayOfWeek.WEDNESDAY,
              DayOfWeek.THURSDAY,
              DayOfWeek.FRIDAY,
            ),
          startTime = LocalTime(8, 0),
          endTime = LocalTime(18, 0),
          exemptPermitZones = listOf("A"),
          source = IntervalSource.REGULATION,
        )
      ),
  )

@VisibleForTesting
internal val meteredSpot =
  ParkingSpot(
    objectId = "2",
    geometry = Geometry(type = "Line", coordinates = listOf(listOf(1.0, 2.0), listOf(3.0, 4.0))),
    streetName = "Post Street",
    blockLimits = "Kearny - Montgomery",
    neighborhood = "Financial District",
    rppAreas = emptyList(),
    sweepingCnn = "54321",
    sweepingSide = StreetSide.RIGHT,
    sweepingSchedules = emptyList(),
    timeline =
      listOf(
        ParkingInterval(
          type = IntervalType.Metered(timeLimitMinutes = 60),
          days =
            setOf(
              DayOfWeek.MONDAY,
              DayOfWeek.TUESDAY,
              DayOfWeek.WEDNESDAY,
              DayOfWeek.THURSDAY,
              DayOfWeek.FRIDAY,
            ),
          startTime = LocalTime(9, 0),
          endTime = LocalTime(18, 0),
          source = IntervalSource.METER,
        ),
        ParkingInterval(
          type = IntervalType.Forbidden(reason = "Tow Away"),
          days =
            setOf(
              DayOfWeek.MONDAY,
              DayOfWeek.TUESDAY,
              DayOfWeek.WEDNESDAY,
              DayOfWeek.THURSDAY,
              DayOfWeek.FRIDAY,
            ),
          startTime = LocalTime(7, 0),
          endTime = LocalTime(9, 0),
          source = IntervalSource.TOW,
        ),
      ),
  )

@Preview(showBackground = true)
@Composable
private fun WatchedSpotDetailContentPreview() {
  ParkBuddyTheme { SpotDetailContent(spot = spot, isInPermitZone = true, onParkHere = {}) }
}

@Preview(showBackground = true)
@Composable
private fun NonWatchedSpotDetailContentPreview() {
  ParkBuddyTheme { SpotDetailContent(spot = spot, isInPermitZone = false, onParkHere = {}) }
}

@Preview(showBackground = true, name = "Metered Spot with Tow Zone")
@Composable
private fun MeteredSpotDetailContentPreview() {
  ParkBuddyTheme { SpotDetailContent(spot = meteredSpot, isInPermitZone = false, onParkHere = {}) }
}

@Preview(showBackground = true, name = "Free Parking (no timeline)")
@Composable
private fun FreeParkingSpotDetailContentPreview() {
  val freeSpot =
    ParkingSpot(
      objectId = "3",
      geometry = Geometry(type = "Line", coordinates = listOf(listOf(1.0, 2.0), listOf(3.0, 4.0))),
      streetName = "Residential Lane",
      blockLimits = "Oak - Pine",
      neighborhood = "Sunset",
      rppAreas = emptyList(),
      sweepingCnn = "99999",
      sweepingSide = StreetSide.LEFT,
      sweepingSchedules = emptyList(),
      timeline = emptyList(),
    )
  ParkBuddyTheme { SpotDetailContent(spot = freeSpot, isInPermitZone = false, onParkHere = {}) }
}
