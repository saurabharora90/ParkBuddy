package dev.bongballe.parkbuddy.data.repository.utils

import com.sun.org.apache.xalan.internal.lib.ExsltDatetime.dayName
import dev.bongballe.parkbuddy.data.repository.utils.DateTimeUtils.formatHour
import dev.bongballe.parkbuddy.model.SweepingSchedule
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object DateTimeUtils {
  fun formatHour(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
  }
}

fun SweepingSchedule.formatSchedule(): String {
  val dayName = weekday.name
  val fromTime = formatHour(fromHour)
  val toTime = formatHour(toHour)

  val activeWeeks = mutableListOf<Int>()
  if (week1) activeWeeks.add(1)
  if (week2) activeWeeks.add(2)
  if (week3) activeWeeks.add(3)
  if (week4) activeWeeks.add(4)
  if (week5) activeWeeks.add(5)

  val weekSuffix = if (activeWeeks.size == 5) {
    ""
  } else {
    " (Weeks ${activeWeeks.joinToString(", ")})"
  }

  return "$dayName$weekSuffix, $fromTime - $toTime"
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

