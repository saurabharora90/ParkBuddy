package dev.bongballe.parkbuddy.data.repository.utils

import dev.bongballe.parkbuddy.model.IntervalSource
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * Resolves overlapping parking rules into a flat, non-overlapping weekly timeline.
 *
 * The resolver takes candidate intervals from multiple data sources (regulations, meters, tow
 * zones) and produces a clean `List<ParkingInterval>` where higher-priority rules have already
 * "punched through" lower-priority ones. Gaps are implicitly [IntervalType.Open] (not stored).
 *
 * Priority hierarchy: FORBIDDEN(4) > RESTRICTED(3) > METERED(2) > LIMITED(1) > OPEN(0)
 *
 * Within the same priority tier, the shorter time limit wins (safer for the user).
 */
object TimelineResolver {

  /**
   * Candidate interval before resolution. Each represents a single raw rule on a single day.
   *
   * @property day The day this candidate applies to.
   * @property startMinute Start time as minutes-since-midnight (0-1439).
   * @property endMinute End time as minutes-since-midnight (0-1439). If < startMinute, wraps past
   *   midnight.
   * @property type The interval type with its priority.
   * @property exemptPermitZones RPP zones exempt from this rule.
   * @property source Which data layer produced this candidate.
   */
  private data class Candidate(
    val day: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int,
    val type: IntervalType,
    val exemptPermitZones: List<String>,
    val source: IntervalSource,
  )

  /** An event in the sweep-line algorithm. */
  private data class Event(val minute: Int, val isStart: Boolean, val candidate: Candidate)

  /**
   * Resolves a list of candidate [ParkingInterval]s into a non-overlapping timeline.
   *
   * @param candidates Raw intervals from regulations, meters, and tow zones. Each may overlap with
   *   others.
   * @return Sorted, non-overlapping intervals. Gaps are [IntervalType.Open] (not stored).
   */
  fun resolve(candidates: List<ParkingInterval>): List<ParkingInterval> {
    if (candidates.isEmpty()) return emptyList()

    // Explode multi-day intervals into per-day candidates, splitting overnight windows
    val perDay = candidates.flatMap { interval -> explodeToDayCandidates(interval) }

    // Resolve each day independently
    val resolvedPerDay =
      DayOfWeek.entries.flatMap { day ->
        val dayCandidates = perDay.filter { it.day == day }
        if (dayCandidates.isEmpty()) return@flatMap emptyList()
        resolveDay(dayCandidates)
      }

    // Merge intervals that share the same (type, startTime, endTime, source, permitZones)
    // but differ only in day sets
    return mergeAcrossDays(resolvedPerDay)
  }

  /**
   * Explodes a multi-day [ParkingInterval] into single-day [Candidate]s.
   *
   * If the interval crosses midnight (startTime > endTime), it's split into two half-day
   * candidates: [startTime, 23:59] on the original day and [00:00, endTime] on the next day.
   */
  private fun explodeToDayCandidates(interval: ParkingInterval): List<Candidate> {
    val startMin = interval.startTime.toMinuteOfDay()
    val endMin = interval.endTime.toMinuteOfDay()

    if (startMin < endMin) {
      // Normal same-day window
      return interval.days.map { day ->
        Candidate(day, startMin, endMin, interval.type, interval.exemptPermitZones, interval.source)
      }
    }

    if (startMin == endMin && startMin == 0) {
      // Full day: 00:00-00:00 (or 00:00-23:59 from normalizeWindow) means all-day enforcement.
      // Only midnight-to-midnight triggers this; other equal pairs (e.g., malformed 8:00-8:00)
      // are treated as zero-width and produce no candidates.
      return interval.days.map { day ->
        Candidate(day, 0, 1440, interval.type, interval.exemptPermitZones, interval.source)
      }
    }

    if (startMin == endMin) {
      // Non-midnight equal times (malformed data). Zero-width window, skip.
      return emptyList()
    }

    // Overnight: split into two halves
    val result = mutableListOf<Candidate>()
    for (day in interval.days) {
      // Evening portion: startTime to midnight
      result.add(
        Candidate(day, startMin, 1440, interval.type, interval.exemptPermitZones, interval.source)
      )
      // Morning portion: midnight to endTime (next day)
      val nextDay = DayOfWeek.entries[(day.ordinal + 1) % 7]
      result.add(
        Candidate(nextDay, 0, endMin, interval.type, interval.exemptPermitZones, interval.source)
      )
    }
    return result
  }

  /**
   * Resolves overlapping candidates for a single day using a sweep-line algorithm.
   *
   * At every time point, the highest-priority candidate wins. Within the same priority, the shorter
   * time limit wins.
   */
  private fun resolveDay(candidates: List<Candidate>): List<Candidate> {
    if (candidates.isEmpty()) return emptyList()

    // Build events
    val events = mutableListOf<Event>()
    for (c in candidates) {
      events.add(Event(c.startMinute, isStart = true, c))
      events.add(Event(c.endMinute, isStart = false, c))
    }
    events.sortWith(compareBy({ it.minute }, { if (it.isStart) 0 else 1 }))

    // Sweep
    val active = mutableListOf<Candidate>()
    val resolved = mutableListOf<Candidate>()
    var prevMinute = -1
    var prevWinner: Candidate? = null

    for (event in events) {
      if (event.minute != prevMinute && prevMinute >= 0 && prevWinner != null) {
        // Emit segment from prevMinute to event.minute with prevWinner
        resolved.add(prevWinner.copy(startMinute = prevMinute, endMinute = event.minute))
      }

      if (event.isStart) {
        active.add(event.candidate)
      } else {
        active.remove(event.candidate)
      }

      prevMinute = event.minute
      prevWinner = pickWinner(active)
    }

    // Merge adjacent segments with same type
    return mergeAdjacentSegments(resolved)
  }

  /**
   * Picks the winning candidate from the active set.
   *
   * Higher priority wins. Within the same priority, shorter time limit wins (safer).
   */
  private fun pickWinner(active: List<Candidate>): Candidate? {
    if (active.isEmpty()) return null
    return active
      .sortedWith(
        compareByDescending<Candidate> { it.type.priority }.thenBy { timeLimitOf(it.type) }
      )
      .first()
  }

  private fun timeLimitOf(type: IntervalType): Int =
    when (type) {
      is IntervalType.Limited -> type.timeLimitMinutes
      is IntervalType.Metered -> type.timeLimitMinutes
      else -> Int.MAX_VALUE
    }

  /** Merges adjacent segments on the same day with identical type and metadata. */
  private fun mergeAdjacentSegments(segments: List<Candidate>): List<Candidate> {
    if (segments.isEmpty()) return emptyList()
    val merged = mutableListOf<Candidate>()
    var current = segments[0]
    for (i in 1 until segments.size) {
      val next = segments[i]
      if (
        current.endMinute == next.startMinute &&
          current.type == next.type &&
          current.source == next.source &&
          current.exemptPermitZones == next.exemptPermitZones
      ) {
        current = current.copy(endMinute = next.endMinute)
      } else {
        if (current.startMinute < current.endMinute) merged.add(current)
        current = next
      }
    }
    if (current.startMinute < current.endMinute) merged.add(current)
    return merged
  }

  /**
   * Merges resolved per-day candidates back into multi-day [ParkingInterval]s.
   *
   * Groups by (type, startTime, endTime, source, permitZones) and unions their day sets.
   */
  private fun mergeAcrossDays(candidates: List<Candidate>): List<ParkingInterval> {
    data class MergeKey(
      val startMinute: Int,
      val endMinute: Int,
      val type: IntervalType,
      val source: IntervalSource,
      val exemptPermitZones: List<String>,
    )

    return candidates
      .groupBy { MergeKey(it.startMinute, it.endMinute, it.type, it.source, it.exemptPermitZones) }
      .map { (key, group) ->
        ParkingInterval(
          type = key.type,
          days = group.map { it.day }.toSet(),
          startTime = minuteToLocalTime(key.startMinute),
          endTime = minuteToLocalTime(key.endMinute.coerceAtMost(1439)),
          exemptPermitZones = key.exemptPermitZones,
          source = key.source,
        )
      }
      .sortedWith(
        compareBy({ it.days.minByOrNull { d -> d.ordinal }?.ordinal ?: 99 }, { it.startTime })
      )
  }

  private fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

  private fun minuteToLocalTime(minute: Int): LocalTime {
    val clamped = minute.coerceIn(0, 1439)
    return LocalTime(clamped / 60, clamped % 60)
  }
}
