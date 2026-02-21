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
    modifier = modifier.background(MaterialTheme.colorScheme.background).padding(16.dp),
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
            text = "PERMIT VALID. TIME LIMITS DO NOT APPLY TO YOU",
            style = MaterialTheme.typography.labelSmall,
            color = SageGreen,
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
          spot.timeLimitHours?.let { timeLimitHours ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = SageGreen,
              )

              val annotatedString = buildAnnotatedString {
                append("Max $timeLimitHours hrs:")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                  append(
                    "${
                      spot.enforcementSchedule.days.joinToString(
                        prefix = " ",
                        transform = { it.name.substring(0, 3) },
                      )
                    },  ${
                      spot.enforcementSchedule.startTime?.hour?.let {
                        DateTimeUtils.formatHour(it)
                      }
                    }-${
                      spot.enforcementSchedule.endTime?.hour?.let {
                        DateTimeUtils.formatHour(it)
                      }
                    }"
                  )
                }
              }

              Text(text = annotatedString, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }

        spot.sweepingSchedules.forEach { schedule -> NoParkingInfo(schedule) }
      }
    }

    ParkBuddyButton(label = "Park Here", onClick = onParkHere, modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun NoParkingInfo(schedule: SweepingSchedule) {
  val now = Clock.System.now()
  val nextCleaning = schedule.nextOccurrence(now)
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
    timeLimitHours = 2,
    enforcementSchedule =
      dev.bongballe.parkbuddy.model.EnforcementSchedule(
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
