package dev.bongballe.parkbuddy.data.repository.utils

import dev.bongballe.parkbuddy.data.repository.utils.DateTimeUtils.formatHour
import dev.bongballe.parkbuddy.model.SweepingSchedule

object DateTimeUtils {
  fun formatHour(hour: Int): String =
    when {
      hour == 0 -> "12 AM"
      hour < 12 -> "$hour AM"
      hour == 12 -> "12 PM"
      else -> "${hour - 12} PM"
    }

  /** Formats a 24h hour and minute as "9:05 AM", "12:30 PM", etc. */
  fun formatHourMinute(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour =
      when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
      }
    val displayMinute = minute.toString().padStart(2, '0')
    return "$displayHour:$displayMinute $amPm"
  }
}

fun SweepingSchedule.formatSchedule(): String {
  val dayName = weekday.name
  val fromTime = formatHour(fromHour)
  val toTime = formatHour(toHour)

  val activeWeeks =
    listOf(week1 to 1, week2 to 2, week3 to 3, week4 to 4, week5 to 5)
      .filter { it.first }
      .map { it.second }

  val weekSuffix =
    if (activeWeeks.size == 5) {
      ""
    } else {
      " (Weeks ${activeWeeks.joinToString(", ")})"
    }

  return "$dayName$weekSuffix, $fromTime - $toTime"
}
