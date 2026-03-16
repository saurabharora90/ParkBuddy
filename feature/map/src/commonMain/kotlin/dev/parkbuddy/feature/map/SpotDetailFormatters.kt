package dev.parkbuddy.feature.map

import androidx.compose.ui.graphics.Color
import dev.bongballe.parkbuddy.data.repository.utils.DateTimeUtils
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.theme.Goldenrod
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.bongballe.parkbuddy.theme.WildIris
import kotlin.time.Duration
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

internal fun formatDayRange(days: Set<DayOfWeek>): String {
  if (days.size == 7) return "Daily"
  if (days.isEmpty()) return ""

  val sorted = days.sortedBy { it.ordinal }
  val isContiguous = sorted.zipWithNext().all { (a, b) -> b.ordinal - a.ordinal == 1 }

  if (isContiguous && sorted.size >= 3) {
    return "${sorted.first().shortName}-${sorted.last().shortName}"
  }

  return sorted.joinToString(", ") { it.shortName }
}

internal val DayOfWeek.shortName: String
  get() = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

internal fun formatLimit(minutes: Int): String =
  when {
    minutes == 0 -> ""
    minutes >= 60 -> {
      val hrs = minutes / 60
      val remainder = minutes % 60
      when {
        remainder == 0 && hrs == 1 -> "1 hr"
        remainder == 0 -> "$hrs hrs"
        else -> "${minutes / 60.0} hrs"
      }
    }

    else -> "$minutes min"
  }

internal fun formatTime(time: LocalTime): String = DateTimeUtils.formatHour(time.hour)

internal fun formatDurationCompact(duration: Duration): String {
  val hours = duration.inWholeHours
  val mins = duration.inWholeMinutes % 60
  return when {
    hours > 0 && mins > 0 -> "${hours}h ${mins}m"
    hours > 0 -> "${hours}h"
    else -> "${mins}m"
  }
}

internal fun formatRelativeTime(duration: Duration): String {
  val hoursUntil = duration.inWholeHours
  return when {
    duration.isNegative() -> "now"
    hoursUntil < 1 -> "in ${duration.inWholeMinutes} min"
    hoursUntil == 1L -> "in 1 hr"
    hoursUntil < 24 -> "in $hoursUntil hrs"
    else -> {
      val days = hoursUntil / 24
      if (days == 1L) "in 1 day" else "in $days days"
    }
  }
}

internal fun intervalDetail(interval: ParkingInterval): String =
  when (val type = interval.type) {
    is IntervalType.Open -> ""
    is IntervalType.Limited -> "${formatLimit(type.timeLimitMinutes)} max"
    is IntervalType.Metered -> {
      if (type.timeLimitMinutes > 0) "${formatLimit(type.timeLimitMinutes)} max, metered"
      else "metered"
    }

    is IntervalType.Restricted -> type.reason.displayText().lowercase()
    is IntervalType.Forbidden -> type.reason.displayText().lowercase()
  }

internal fun intervalColor(type: IntervalType): Color =
  when (type) {
    is IntervalType.Open -> SagePrimary
    is IntervalType.Limited -> WildIris
    is IntervalType.Metered -> Goldenrod
    is IntervalType.Restricted -> Terracotta
    is IntervalType.Forbidden -> Terracotta
  }
