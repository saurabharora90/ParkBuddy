package dev.bongballe.parkbuddy.data.sf

import kotlin.math.roundToInt
import kotlinx.datetime.LocalTime

/** Parses time strings from SF parking data APIs. */
object TimeParser {

  /** Parses regulation hours like "800" -> 08:00, "1800" -> 18:00, "2400" -> 00:00. */
  fun parseRegulationTime(s: String?): LocalTime? {
    val digits = s?.filter { it.isDigit() }?.toIntOrNull() ?: return null
    var h = digits / 100
    val m = digits % 100
    if (h == 24) h = 0
    return LocalTime(h.coerceIn(0, 23), m.coerceIn(0, 59))
  }

  /** Parses ArcGIS numeric hours like 900.0 -> 09:00, 1600.0 -> 16:00, 2400.0 -> 00:00. */
  fun parseRegulationTime(d: Double?): LocalTime? {
    if (d == null) return null
    val i = d.toInt()
    var h = i / 100
    val m = i % 100
    if (h == 24) h = 0
    return LocalTime(h.coerceIn(0, 23), m.coerceIn(0, 59))
  }

  /** Parses simple am/pm strings like "9am", "4pm", "12am". */
  fun parseSimpleAmPm(s: String?): LocalTime? {
    if (s.isNullOrBlank()) return null
    val normalized = s.trim().lowercase()
    val amPm =
      when {
        normalized.endsWith("am") -> "am"
        normalized.endsWith("pm") -> "pm"
        else -> return null
      }
    val hourStr = normalized.removeSuffix(amPm).trim()
    val hour = hourStr.split(":").firstOrNull()?.toIntOrNull() ?: return null
    return amPmToLocalTime(hour, amPm)
  }

  /** Core AM/PM conversion. Used by [parseSimpleAmPm] and [BlockfaceRateParser]. */
  fun amPmToLocalTime(hour: Int, amPm: String): LocalTime? {
    val h =
      when {
        amPm.equals("am", true) && hour == 12 -> 0
        amPm.equals("pm", true) && hour < 12 -> hour + 12
        else -> hour
      }
    return if (h in 0..23) LocalTime(h, 0) else null
  }

  /** Parses legacy meter times like "7:00 AM", "12:00 PM", "12:00 AM". */
  fun parseMeterTime(s: String?): LocalTime? {
    if (s == null) return null
    return try {
      val parts = s.trim().split(" ")
      val timeParts = parts[0].split(":")
      var h = timeParts[0].toInt()
      val m = if (timeParts.size > 1) timeParts[1].toInt() else 0
      if (parts.size > 1) {
        val ampm = parts[1].uppercase()
        if (ampm == "PM" && h < 12) h += 12 else if (ampm == "AM" && h == 12) h = 0
      }
      LocalTime(h % 24, m % 60)
    } catch (_: Exception) {
      null
    }
  }

  /** Parses 24h times like "0:00", "8:00", "15:00", "24:00" from the Meter Policies API. */
  fun parsePolicyTime(s: String?): LocalTime? {
    if (s.isNullOrBlank()) return null
    return try {
      val parts = s.trim().split(":")
      var h = parts[0].toInt()
      val m = if (parts.size > 1) parts[1].toInt() else 0
      if (h == 24) h = 0
      LocalTime(h.coerceIn(0, 23), m.coerceIn(0, 59))
    } catch (_: Exception) {
      null
    }
  }

  /** Parses ArcGIS HRLIMIT (hours as Double) to minutes. */
  fun parseTimeLimitHours(d: Double?): Int {
    if (d == null || d <= 0.0) return 0
    return (d * 60).roundToInt()
  }

  /**
   * Normalizes a start/end time pair, handling the 00:00-00:00 full-day case.
   *
   * If both start and end are midnight, this represents a 24-hour window. We expand end to 23:59 so
   * the interval covers the full day.
   */
  fun normalizeWindow(start: LocalTime, end: LocalTime): Pair<LocalTime, LocalTime> {
    return if (start == end && start == LocalTime(0, 0)) {
      LocalTime(0, 0) to LocalTime(23, 59)
    } else {
      start to end
    }
  }
}
