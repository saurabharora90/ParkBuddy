package dev.bongballe.parkbuddy.data.sf

import dev.bongballe.parkbuddy.model.IntervalSource
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.model.ProhibitionReason

/**
 * Parses RATE_SCHED HTML from the ArcGIS blockface rates layer (sfpark_ODS/MapServer/4).
 *
 * The HTML contains a table with time-of-day rows, each with a rate ("Free", "$2.25 per hour", "no
 * parking") and an optional time limit. We extract metered and no-parking windows as
 * [ParkingInterval]s. Free windows are skipped (gaps are implicitly open).
 *
 * Example HTML:
 * ```
 * <table>
 *   <tr><th>Time of day</th><th>Rate</th><th>General metered parking time limit</th>
 *   <tr><td>12am - 8am<td>Free<td>None</td>
 *   <tr><td>8am - 6pm<td>$2.25 per hour<td>None</td>
 *   <tr><td>6pm - 12am<td>Free<td>None</td>
 * </table>
 * ```
 */
object BlockfaceRateParser {

  private val ROW_REGEX = Regex("""<td>([^<]+)<td>([^<]+)<td>([^<]*)""", RegexOption.IGNORE_CASE)
  private val TIME_REGEX =
    Regex("""(\d{1,2})(am|pm)\s*-\s*(\d{1,2})(am|pm)""", RegexOption.IGNORE_CASE)

  fun parse(rateSched: String?, rppAreas: List<String>): List<ParkingInterval> {
    if (rateSched.isNullOrBlank()) return emptyList()

    val intervals = mutableListOf<ParkingInterval>()

    for (match in ROW_REGEX.findAll(rateSched)) {
      val timeStr = match.groupValues[1].trim()
      val rateStr = match.groupValues[2].trim()

      val timeMatch = TIME_REGEX.find(timeStr) ?: continue
      val start =
        TimeParser.amPmToLocalTime(timeMatch.groupValues[1].toInt(), timeMatch.groupValues[2])
      val end =
        TimeParser.amPmToLocalTime(timeMatch.groupValues[3].toInt(), timeMatch.groupValues[4])
      if (start == null || end == null) continue

      val rateLower = rateStr.lowercase()
      when {
        rateLower == "free" -> {}
        rateLower.contains("no parking") || rateLower.contains("no stopping") -> {
          intervals.add(
            ParkingInterval(
              type = IntervalType.Forbidden(ProhibitionReason.TOW_AWAY),
              days = ALL_DAYS,
              startTime = start,
              endTime = end,
              source = IntervalSource.TOW,
            )
          )
        }
        rateLower.contains("per hour") || rateLower.contains("$") -> {
          intervals.add(
            ParkingInterval(
              type = IntervalType.Metered(timeLimitMinutes = 0),
              days = ALL_DAYS,
              startTime = start,
              endTime = end,
              exemptPermitZones = rppAreas,
              source = IntervalSource.METER,
            )
          )
        }
      }
    }
    return intervals
  }
}
