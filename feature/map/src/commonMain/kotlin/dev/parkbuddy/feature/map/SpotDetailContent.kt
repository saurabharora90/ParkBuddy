package dev.parkbuddy.feature.map

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import dev.bongballe.parkbuddy.core.navigation.Navigator
import dev.bongballe.parkbuddy.data.repository.utils.formatSchedule
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.IntervalSource
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ProhibitionReason
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.bongballe.parkbuddy.model.Weekday
import dev.bongballe.parkbuddy.theme.Goldenrod
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.bongballe.parkbuddy.theme.SageGreen
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.bongballe.parkbuddy.theme.WildIris
import dev.parkbuddy.core.ui.DayTimelineBar
import dev.parkbuddy.core.ui.ParkBuddyButton
import dev.parkbuddy.core.ui.ParkBuddyIcons
import dev.parkbuddy.core.ui.SquircleIcon
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/** Entry point that collects state from the ViewModel. */
@Composable
internal fun SpotDetailContent(
  viewModel: SpotDetailViewModel,
  navigator: Navigator,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.stateFlow.collectAsState()
  SpotDetailContent(
    state = state,
    onParkHere = {
      viewModel.parkHere()
      navigator.goBack()
    },
    modifier = modifier,
  )
}

@Composable
internal fun SpotDetailContent(
  state: SpotDetailState,
  onParkHere: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .background(MaterialTheme.colorScheme.background)
        .padding(bottom = 16.dp)
        .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    SpotHeader(state.spot)

    CurrentStateCard(state)

    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
      Column(
        modifier = Modifier.padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (state.sortedIntervals.isNotEmpty() || state.sweepingDisplay.isNotEmpty()) {
          Text(
            text = "PARKING RULES & SCHEDULES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
          )

          state.sortedIntervals.forEach { (interval, isActive) ->
            IntervalRow(interval = interval, isActive = isActive)
          }

          state.sweepingDisplay.forEach { display -> SweepingRow(display) }
        }
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

@Composable
private fun CurrentStateCard(state: SpotDetailState) {
  val upcoming = state.upcoming
  val isImminent = state.isImminent
  val now = state.now

  when (val restriction = state.restrictionState) {
    is ParkingRestrictionState.CleaningActive -> {
      val remaining = restriction.cleaningEnd - now
      StateCard(
        icon = ParkBuddyIcons.Error,
        accentColor = Terracotta,
        title = "YOU CANNOT PARK HERE",
        details =
          buildList {
            add("Reason" to "Street Cleaning")
            if (!remaining.isNegative()) add("Remaining" to formatDurationCompact(remaining))
          },
        showBorder = true,
        segments = state.timelineSegments,
        currentMinute = state.currentMinute,
      )
    }

    is ParkingRestrictionState.PermitSafe -> {
      val permitZone = state.permitZone.orEmpty()
      if (isImminent && upcoming != null) {
        StateCard(
          icon = ParkBuddyIcons.Warning,
          accentColor = Goldenrod,
          title = "FREE FOR YOU",
          details =
            buildList {
              add("Permit $permitZone active" to "")
              add("${upcoming.reason} ${formatRelativeTime(upcoming.duration)}" to "")
              add("Window" to upcoming.window)
            },
        )
      } else {
        StateCard(
          icon = ParkBuddyIcons.SafetyCheck,
          accentColor = SageGreen,
          title = "FREE FOR YOU",
          details =
            buildList {
              add("Permit $permitZone active" to "")
              upcoming?.let { add("Next: ${it.label}" to "") }
            },
        )
      }
    }

    is ParkingRestrictionState.Forbidden -> {
      StateCard(
        icon = ParkBuddyIcons.Error,
        accentColor = Terracotta,
        title = "YOU CANNOT PARK HERE",
        details = buildList { add("Reason" to restriction.reason.displayText()) },
        showBorder = true,
        segments = state.timelineSegments,
        currentMinute = state.currentMinute,
      )
    }

    is ParkingRestrictionState.ForbiddenUpcoming -> {
      val startsIn = restriction.startsAt - now
      StateCard(
        icon = ParkBuddyIcons.Error,
        accentColor = Terracotta,
        title = "RESTRICTION AHEAD",
        details =
          buildList {
            add("Reason" to restriction.reason.displayText())
            if (!startsIn.isNegative()) add("Starts" to formatRelativeTime(startsIn))
          },
        segments = state.timelineSegments,
        currentMinute = state.currentMinute,
      )
    }

    is ParkingRestrictionState.ActiveTimed -> {
      val accentColor = if (restriction.paymentRequired) Goldenrod else WildIris
      val title = if (restriction.paymentRequired) "PAY AT METER" else "TIME LIMITED"
      val remaining = restriction.expiry - now
      val enforcement = if (!remaining.isNegative()) formatDurationCompact(remaining) else "Expired"

      StateCard(
        icon = ParkBuddyIcons.AccessTime,
        accentColor = accentColor,
        title = title,
        details =
          buildList {
            add("Time remaining" to enforcement)
            if (restriction.paymentRequired) add("Payment" to "Required")
          },
        segments = state.timelineSegments,
        currentMinute = state.currentMinute,
      )
    }

    is ParkingRestrictionState.PendingTimed -> {
      val startsIn = restriction.startsAt - now
      val accentColor = if (restriction.paymentRequired) Goldenrod else WildIris
      val title = if (restriction.paymentRequired) "METERED SOON" else "TIME LIMIT SOON"
      StateCard(
        icon = ParkBuddyIcons.AccessTime,
        accentColor = accentColor,
        title = title,
        details =
          buildList {
            add("Starts" to formatRelativeTime(startsIn))
            if (restriction.paymentRequired) add("Payment" to "Required")
          },
        segments = state.timelineSegments,
        currentMinute = state.currentMinute,
      )
    }

    is ParkingRestrictionState.Unrestricted -> {
      if (isImminent && upcoming != null) {
        StateCard(
          icon = ParkBuddyIcons.Warning,
          accentColor = Goldenrod,
          title = "FREE PARKING",
          details =
            buildList {
              add("${upcoming.reason} ${formatRelativeTime(upcoming.duration)}" to "")
              add("Window" to upcoming.window)
            },
          segments = state.timelineSegments,
          currentMinute = state.currentMinute,
        )
      } else {
        StateCard(
          icon = ParkBuddyIcons.CheckCircle,
          accentColor = SagePrimary,
          title = "FREE PARKING",
          details =
            buildList {
              add("No restrictions right now" to "")
              upcoming?.let { add("Next: ${it.label}" to "") }
            },
          segments = state.timelineSegments,
          currentMinute = state.currentMinute,
        )
      }
    }
  }
}

@Composable
private fun StateCard(
  icon: ImageVector,
  accentColor: Color,
  title: String,
  details: List<Pair<String, String>>,
  showBorder: Boolean = false,
  segments: List<dev.parkbuddy.core.ui.TimelineSegment> = emptyList(),
  currentMinute: Int = 0,
) {
  val cardModifier =
    if (showBorder)
      Modifier.fillMaxWidth()
        .border(width = 2.dp, color = accentColor, shape = MaterialTheme.shapes.large)
    else Modifier.fillMaxWidth()

  Card(
    modifier = cardModifier,
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = Color.White),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SquircleIcon(
          icon = icon,
          size = 48.dp,
          shape = RoundedCornerShape(12.dp),
          iconTint = accentColor,
          backgroundTint = accentColor.copy(alpha = 0.15f),
        )
        Column {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor,
          )
          details.forEach { (key, value) ->
            if (value.isEmpty()) {
              Text(
                text = key,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              Text(
                text =
                  buildAnnotatedString {
                    append("$key: ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value) }
                  },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      if (segments.isNotEmpty()) {
        DayTimelineBar(
          segments = segments,
          currentMinute = currentMinute,
          freeColor = SagePrimary.copy(alpha = 0.15f),
        )
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Timeline Rows
// ---------------------------------------------------------------------------

@Composable
private fun IntervalRow(
  interval: ParkingInterval,
  isActive: Boolean,
  modifier: Modifier = Modifier,
) {
  val tint = intervalColor(interval.type)
  val icon = intervalIcon(interval.type)

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

    val dayRange = formatDayRange(interval.days)
    val detail = intervalDetail(interval)
    val time = "${formatTime(interval.startTime)}-${formatTime(interval.endTime)}"

    val annotatedString = buildAnnotatedString {
      withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(dayRange) }
      if (detail.isNotEmpty()) append(" ($detail)")
      append(": $time")
    }

    Text(text = annotatedString, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun SweepingRow(display: SweepingDisplay, modifier: Modifier = Modifier) {
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
          append(display.schedule.formatSchedule())
        }
      }
      Text(text = noParkingTiming, style = MaterialTheme.typography.bodyMedium)

      Row {
        Text(
          text = "STREET CLEANING",
          style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
          modifier = Modifier.background(Terracotta.copy(alpha = 0.2f)).padding(2.dp),
        )

        if (display.isActive) {
          Text(
            text = " \u2022 IN PROGRESS",
            style =
              MaterialTheme.typography.bodyMedium.copy(
                color = Terracotta,
                fontWeight = FontWeight.Bold,
              ),
          )
        } else {
          display.relativeTimeText?.let { timeText ->
            Text(text = " \u2022 $timeText", style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    }
  }
}

@Composable
private fun intervalIcon(type: IntervalType): ImageVector =
  when (type) {
    is IntervalType.Open -> ParkBuddyIcons.CheckCircle
    is IntervalType.Limited -> ParkBuddyIcons.AccessTime
    is IntervalType.Metered -> ParkBuddyIcons.AccessTime
    is IntervalType.Restricted -> ParkBuddyIcons.Warning
    is IntervalType.Forbidden -> ParkBuddyIcons.Error
  }

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@VisibleForTesting
internal val previewSpot =
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
          days = DayOfWeek.entries.toSet(),
          startTime = LocalTime(8, 0),
          endTime = LocalTime(18, 0),
          exemptPermitZones = listOf("A"),
          source = IntervalSource.REGULATION,
        )
      ),
  )

@VisibleForTesting
internal val previewMeteredSpot =
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
          days = DayOfWeek.entries.toSet(),
          startTime = LocalTime(9, 0),
          endTime = LocalTime(18, 0),
          source = IntervalSource.METER,
        ),
        ParkingInterval(
          type = IntervalType.Forbidden(reason = ProhibitionReason.TOW_AWAY),
          days = DayOfWeek.entries.toSet(),
          startTime = LocalTime(7, 0),
          endTime = LocalTime(9, 0),
          source = IntervalSource.TOW,
        ),
      ),
  )

private fun previewState(
  spot: ParkingSpot = previewSpot,
  permitZone: String? = null,
): SpotDetailState {
  val now = Clock.System.now()
  return evaluate(spot, permitZone, now)
}

@Preview(showBackground = true)
@Composable
private fun SpotDetailContentPreview_PermitZone() {
  ParkBuddyTheme { SpotDetailContent(state = previewState(permitZone = "A"), onParkHere = {}) }
}

@Preview(showBackground = true)
@Composable
private fun SpotDetailContentPreview_TimeLimited() {
  ParkBuddyTheme { SpotDetailContent(state = previewState(), onParkHere = {}) }
}

@Preview(showBackground = true, name = "Metered Spot with Tow Zone")
@Composable
private fun SpotDetailContentPreview_Metered() {
  ParkBuddyTheme {
    SpotDetailContent(state = previewState(spot = previewMeteredSpot), onParkHere = {})
  }
}

@Preview(showBackground = true, name = "Free Parking (no timeline)")
@Composable
private fun SpotDetailContentPreview_Free() {
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
  ParkBuddyTheme { SpotDetailContent(state = previewState(spot = freeSpot), onParkHere = {}) }
}
