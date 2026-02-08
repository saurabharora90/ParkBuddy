package dev.bongballe.parkbuddy.data.repository.utils

import dev.bongballe.parkbuddy.data.repository.utils.DateTimeUtils.formatHour
import dev.bongballe.parkbuddy.model.SweepingSchedule
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object DateTimeUtils {
  fun formatHour(hour: Int): String = when {
    hour == 0 -> "12:00 AM"
    hour < 12 -> "$hour:00 AM"
    hour == 12 -> "12:00 PM"
    else -> "${hour - 12}:00 PM"
  }
}

fun SweepingSchedule.formatSchedule(): String {
  val dayName = weekday.name
  val fromTime = formatHour(fromHour)
  val toTime = formatHour(toHour)
  return "$dayName, $fromTime - $toTime"
}

fun SweepingSchedule.formatWithDate(
  instant: Instant,
  zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
  val localDateTime = instant.toLocalDateTime(zone)
  val dayOfWeek =
    localDateTime.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
  val monthName =
    localDateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
  val dayOfMonth = localDateTime.day
  val fromTime = formatHour(fromHour)
  val toTime = formatHour(toHour)
  return "$dayOfWeek, $monthName $dayOfMonth ($fromTime - $toTime)"
}

