package dev.bongballe.parkbuddy.data.sf

import kotlinx.datetime.DayOfWeek

/**
 * Parses day-of-week strings from SF Open Data into [Set]<[DayOfWeek]>.
 *
 * The regulations and meter APIs use inconsistent formats:
 * - Ranges: "M-F", "M-Sa", "M-Su", "M-S"
 * - Comma-separated: "M, TH", "Mo,Tu,We,Th,Fr"
 * - Single: "Sa", "Su"
 * - Special: "School Days", "Giants Day", "Business Hours"
 */
object DayParser {

  private val WEEKDAYS =
    setOf(
      DayOfWeek.MONDAY,
      DayOfWeek.TUESDAY,
      DayOfWeek.WEDNESDAY,
      DayOfWeek.THURSDAY,
      DayOfWeek.FRIDAY,
    )

  private val ALL_DAYS = DayOfWeek.entries.toSet()

  // Ordered Mon-Sun so range expansion works with ordinals
  private val SHORT_TO_DAY =
    mapOf(
      "M" to DayOfWeek.MONDAY,
      "MO" to DayOfWeek.MONDAY,
      "MON" to DayOfWeek.MONDAY,
      "T" to DayOfWeek.TUESDAY,
      "TU" to DayOfWeek.TUESDAY,
      "TUE" to DayOfWeek.TUESDAY,
      "TUES" to DayOfWeek.TUESDAY,
      "W" to DayOfWeek.WEDNESDAY,
      "WE" to DayOfWeek.WEDNESDAY,
      "WED" to DayOfWeek.WEDNESDAY,
      "TH" to DayOfWeek.THURSDAY,
      "THU" to DayOfWeek.THURSDAY,
      "F" to DayOfWeek.FRIDAY,
      "FR" to DayOfWeek.FRIDAY,
      "FRI" to DayOfWeek.FRIDAY,
      "S" to DayOfWeek.SATURDAY,
      "SA" to DayOfWeek.SATURDAY,
      "SAT" to DayOfWeek.SATURDAY,
      "SU" to DayOfWeek.SUNDAY,
      "SUN" to DayOfWeek.SUNDAY,
    )

  // Event-based schedules that map to reasonable defaults
  private val EVENT_DEFAULTS = mapOf("SCHOOL DAYS" to WEEKDAYS, "BUSINESS HOURS" to WEEKDAYS)

  // Event-based schedules we can't evaluate, return null to signal "skip this"
  private val UNEVALUABLE_EVENTS =
    setOf("GIANTS DAY", "GIANTS NIGHT", "PERFORMANCE", "POSTED EVENTS", "POSTED SERVICES")

  /**
   * Parses regulation-style day strings (e.g. "M-F", "M-Sa", "M, TH").
   *
   * Returns all 7 days if the input is null, blank, or unparseable (safe fallback for regulations).
   */
  fun parseRegulationDays(daysStr: String?): Set<DayOfWeek> {
    if (daysStr.isNullOrBlank()) return ALL_DAYS
    val result = parseInternal(daysStr.trim().uppercase())
    return result ?: ALL_DAYS
  }

  /**
   * Parses meter-schedule-style day strings (e.g. "Mo,Tu,We,Th,Fr", "School Days").
   *
   * Returns null for unevaluable events (game days, posted events) so the caller can skip them.
   * Returns all 7 days if the input is null or blank.
   */
  fun parseMeterDays(daysStr: String?): Set<DayOfWeek>? {
    if (daysStr.isNullOrBlank()) return ALL_DAYS
    val normalized = daysStr.trim().uppercase()

    // Check unevaluable events first
    if (UNEVALUABLE_EVENTS.any { normalized.contains(it) }) return null

    // Check event defaults
    EVENT_DEFAULTS.entries
      .firstOrNull { normalized.contains(it.key) }
      ?.let {
        return it.value
      }

    val result = parseInternal(normalized)
    return result ?: ALL_DAYS
  }

  private fun parseInternal(normalized: String): Set<DayOfWeek>? {
    if (normalized == "DAILY" || normalized == "EVERYDAY") return ALL_DAYS
    if (normalized == "WEEKDAYS") return WEEKDAYS

    // Try range format first: "X-Y"
    val rangeMatch = Regex("^([A-Z]+)-([A-Z]+)$").find(normalized)
    if (rangeMatch != null) {
      val start = SHORT_TO_DAY[rangeMatch.groupValues[1]]
      val end = SHORT_TO_DAY[rangeMatch.groupValues[2]]
      if (start != null && end != null) {
        return expandRange(start, end)
      }
    }

    // Try comma/space-separated tokens: "M, TH" or "Mo,Tu,We"
    val tokens = normalized.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
    val days = mutableSetOf<DayOfWeek>()
    for (token in tokens) {
      // Each token could be a single day or a range
      val tokenRange = Regex("^([A-Z]+)-([A-Z]+)$").find(token)
      if (tokenRange != null) {
        val start = SHORT_TO_DAY[tokenRange.groupValues[1]]
        val end = SHORT_TO_DAY[tokenRange.groupValues[2]]
        if (start != null && end != null) {
          days.addAll(expandRange(start, end))
        }
      } else {
        SHORT_TO_DAY[token]?.let { days.add(it) }
      }
    }

    return days.ifEmpty { null }
  }

  /** Expands a day range (e.g. MONDAY to SATURDAY) inclusive of both endpoints. */
  private fun expandRange(start: DayOfWeek, end: DayOfWeek): Set<DayOfWeek> {
    val days = mutableSetOf<DayOfWeek>()
    val ordered = DayOfWeek.entries // MONDAY(0) .. SUNDAY(6)
    val startIdx = ordered.indexOf(start)
    val endIdx = ordered.indexOf(end)
    if (startIdx <= endIdx) {
      for (i in startIdx..endIdx) days.add(ordered[i])
    } else {
      // Wrap around (e.g. Sat-Mon = Sat, Sun, Mon)
      for (i in startIdx until ordered.size) days.add(ordered[i])
      for (i in 0..endIdx) days.add(ordered[i])
    }
    return days
  }
}
