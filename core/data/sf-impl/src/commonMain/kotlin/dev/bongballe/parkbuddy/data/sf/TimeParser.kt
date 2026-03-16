package dev.bongballe.parkbuddy.data.sf

import kotlinx.datetime.LocalTime

/**
 * Parses time strings from SF Open Data APIs.
 *
 * Handles both regulation format ("800", "1800", "2400") and meter format ("7:00 AM", "12:00 PM").
 */
object TimeParser {

  /** Parses regulation hours like "800" -> 08:00, "1800" -> 18:00, "2400" -> 00:00. */
  fun parseRegulationTime(s: String?): LocalTime? {
    val digits = s?.filter { it.isDigit() }?.toIntOrNull() ?: return null
    var h = digits / 100
    val m = digits % 100
    if (h == 24) h = 0
    return LocalTime(h.coerceIn(0, 23), m.coerceIn(0, 59))
  }

  /** Parses meter times like "7:00 AM", "12:00 PM", "12:00 AM". */
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

  /**
   * Parses regulation time limit like "2" -> 120 min, "0.5" -> 30 min.
   *
   * The SF API stores hours as a decimal string in the `hrlimit` field.
   */
  fun parseTimeLimit(l: String?): Int? {
    if (l == null) return null
    val d = l.toDoubleOrNull()
    return if (d != null) (d * 60).toInt()
    else l.filter { it.isDigit() }.toIntOrNull()?.let { it * 60 }
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
