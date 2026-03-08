package dev.parkbuddy.feature.map

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SafetyCheck
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import dev.bongballe.parkbuddy.model.ParkingRegulation
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.StreetSide
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.bongballe.parkbuddy.model.Weekday
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.bongballe.parkbuddy.theme.SageGreen
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.parkbuddy.core.ui.ParkBuddyButton
import kotlin.time.Clock
import kotlinx.datetime.LocalTime

@Composable
internal fun SpotDetailContent(
  spot: ParkingSpot,
  isInPermitZone: Boolean,
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

    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
      if (isInPermitZone) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          modifier =
            Modifier.fillMaxWidth().background(SagePrimary.copy(alpha = 0.2f)).padding(16.dp),
        ) {
          Icon(imageVector = Icons.Default.SafetyCheck, contentDescription = null, tint = SageGreen)
          Text(
            text = "PERMIT ${spot.rppArea} VALID. TIME LIMITS DO NOT APPLY.",
            style = MaterialTheme.typography.labelSmall,
            color = SageGreen,
          )
        }
      } else if (
        spot.regulation == ParkingRegulation.PAY_OR_PERMIT ||
          spot.regulation == ParkingRegulation.METERED
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          modifier =
            Modifier.fillMaxWidth().background(Terracotta.copy(alpha = 0.1f)).padding(16.dp),
        ) {
          Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = Terracotta)
          Text(
            text = "PAY AT METER.",
            style = MaterialTheme.typography.labelSmall,
            color = Terracotta,
          )
        }
      }

      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (!isInPermitZone) {
          Text(
            text = "PARKING RULES & SCHEDULES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          // Show standard time restrictions
          spot.timedRestriction?.let { restriction ->
            RestrictionRow(
              icon = Icons.Default.AccessTime,
              label = "Max ${restriction.limitHours} hrs:",
              days = restriction.days,
              startTime = restriction.startTime,
              endTime = restriction.endTime,
            )
          }

          // Show meter schedules
          spot.meterSchedules.forEach { schedule ->
            RestrictionRow(
              icon = Icons.Default.AccessTime,
              label =
                if (schedule.isTowZone) "TOW AWAY:" else "Max ${schedule.timeLimitMinutes} min:",
              days = schedule.days,
              startTime = schedule.startTime,
              endTime = schedule.endTime,
              tint = if (schedule.isTowZone) Terracotta else SageGreen,
            )
          }
        }

        spot.sweepingSchedules.forEach { schedule -> NoParkingInfo(schedule) }
      }
    }

    ParkBuddyButton(label = "Park Here", onClick = onParkHere, modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun RestrictionRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  days: Set<kotlinx.datetime.DayOfWeek>,
  startTime: LocalTime?,
  endTime: LocalTime?,
  tint: Color = SageGreen,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = tint)

    val annotatedString = buildAnnotatedString {
      append(label)
      withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
        append(
          "${
            days.joinToString(
              prefix = " ",
              transform = { it.name.substring(0, 3) },
            )
          },  ${
            startTime?.let { DateTimeUtils.formatHour(it.hour) } ?: "00:00"
          }-${
            endTime?.let { DateTimeUtils.formatHour(it.hour) } ?: "23:59"
          }"
        )
      }
    }

    Text(text = annotatedString, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun NoParkingInfo(schedule: SweepingSchedule) {
  val now = Clock.System.now()
  val nextCleaning = schedule.nextOccurrence(now)
  val isActive = schedule.isWithinWindow(now)

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = Terracotta)

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

@VisibleForTesting
internal val spot =
  ParkingSpot(
    objectId = "1",
    geometry = Geometry(type = "Line", coordinates = listOf(listOf(1.0, 2.0), listOf(3.0, 4.0))),
    streetName = "Market Street",
    blockLimits = "1st Ave - 2nd Ave",
    neighborhood = "Downtown",
    regulation = ParkingRegulation.TIME_LIMITED,
    rppArea = "A",
    timedRestriction =
      dev.bongballe.parkbuddy.model.TimedRestriction(
        limitHours = 2,
        days =
          setOf(
            kotlinx.datetime.DayOfWeek.MONDAY,
            kotlinx.datetime.DayOfWeek.TUESDAY,
            kotlinx.datetime.DayOfWeek.WEDNESDAY,
            kotlinx.datetime.DayOfWeek.THURSDAY,
            kotlinx.datetime.DayOfWeek.FRIDAY,
          ),
        startTime = LocalTime(8, 0),
        endTime = LocalTime(18, 0),
      ),
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

@VisibleForTesting
internal val meteredSpot =
  ParkingSpot(
    objectId = "2",
    geometry = Geometry(type = "Line", coordinates = listOf(listOf(1.0, 2.0), listOf(3.0, 4.0))),
    streetName = "Post Street",
    blockLimits = "Kearny - Montgomery",
    neighborhood = "Financial District",
    regulation = ParkingRegulation.METERED,
    rppArea = null,
    timedRestriction = null,
    sweepingCnn = "54321",
    sweepingSide = StreetSide.RIGHT,
    sweepingSchedules = emptyList(),
    meterSchedules =
      listOf(
        dev.bongballe.parkbuddy.model.MeterSchedule(
          days =
            setOf(
              kotlinx.datetime.DayOfWeek.MONDAY,
              kotlinx.datetime.DayOfWeek.TUESDAY,
              kotlinx.datetime.DayOfWeek.WEDNESDAY,
              kotlinx.datetime.DayOfWeek.THURSDAY,
              kotlinx.datetime.DayOfWeek.FRIDAY,
            ),
          startTime = LocalTime(9, 0),
          endTime = LocalTime(18, 0),
          timeLimitMinutes = 60,
        ),
        dev.bongballe.parkbuddy.model.MeterSchedule(
          days =
            setOf(
              kotlinx.datetime.DayOfWeek.MONDAY,
              kotlinx.datetime.DayOfWeek.TUESDAY,
              kotlinx.datetime.DayOfWeek.WEDNESDAY,
              kotlinx.datetime.DayOfWeek.THURSDAY,
              kotlinx.datetime.DayOfWeek.FRIDAY,
            ),
          startTime = LocalTime(7, 0),
          endTime = LocalTime(9, 0),
          timeLimitMinutes = 0,
          isTowZone = true,
        ),
      ),
  )
