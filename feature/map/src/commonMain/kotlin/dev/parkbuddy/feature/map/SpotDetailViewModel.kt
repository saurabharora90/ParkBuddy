package dev.parkbuddy.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.data.repository.ParkingManager
import dev.bongballe.parkbuddy.data.repository.utils.DateTimeUtils
import dev.bongballe.parkbuddy.data.repository.utils.ParkingRestrictionEvaluator
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.SweepingSchedule
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.parkbuddy.core.ui.TimelineSegment
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class SpotDetailState(
  val spot: ParkingSpot,
  val restrictionState: ParkingRestrictionState,
  val permitZone: String?,
  val timelineSegments: List<TimelineSegment>,
  val currentMinute: Int,
  val upcoming: UpcomingEnforcement?,
  val isImminent: Boolean,
  val sortedIntervals: List<IntervalDisplay>,
  val sweepingDisplay: List<SweepingDisplay>,
)

data class IntervalDisplay(val interval: ParkingInterval, val isActive: Boolean)

data class SweepingDisplay(
  val schedule: SweepingSchedule,
  val isActive: Boolean,
  val relativeTimeText: String?,
)

data class UpcomingEnforcement(
  val duration: Duration,
  val reason: String,
  val window: String,
  val label: String,
)

private const val IMMINENT_THRESHOLD_HOURS = 3L

@AssistedInject
class SpotDetailViewModel(
  private val parkingManager: ParkingManager,
  @Assisted private val spot: ParkingSpot,
  @Assisted private val userPermitZone: String?,
) : ViewModel() {

  val stateFlow: StateFlow<SpotDetailState> =
    tickerFlow()
      .map { now -> evaluate(spot, userPermitZone, now) }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        evaluate(spot, userPermitZone, Clock.System.now()),
      )

  fun parkHere() {
    viewModelScope.launch { parkingManager.parkHere(spot) }
  }

  @AssistedFactory
  @ManualViewModelAssistedFactoryKey(Factory::class)
  @ContributesIntoMap(AppScope::class)
  fun interface Factory : ManualViewModelAssistedFactory {
    fun create(spot: ParkingSpot, userPermitZone: String?): SpotDetailViewModel
  }
}

private fun tickerFlow(periodMs: Long = 30_000L): Flow<Instant> = flow {
  while (true) {
    emit(Clock.System.now())
    delay(periodMs)
  }
}

// ---------------------------------------------------------------------------
// Pure evaluation function (internal for testing)
// ---------------------------------------------------------------------------

internal fun evaluate(
  spot: ParkingSpot,
  permitZone: String?,
  now: Instant,
  zone: TimeZone = TimeZone.currentSystemDefault(),
): SpotDetailState {
  val restrictionState =
    ParkingRestrictionEvaluator.evaluate(
      spot = spot,
      userPermitZone = permitZone,
      parkedAt = now,
      currentTime = now,
      zone = zone,
    )

  val currentMinute = currentMinuteOfDay(now, zone)
  val allSegments = buildTodaySegments(spot, now, zone)
  // Permit holders only care about cleaning/forbidden segments, not meters/limits
  val segments =
    if (restrictionState is ParkingRestrictionState.PermitSafe)
      allSegments.filter { it.color == Terracotta }
    else allSegments
  val isPermitSafe = restrictionState is ParkingRestrictionState.PermitSafe
  val upcoming = findNextEnforcement(spot, now, zone, isPermitSafe)
  val isImminent = upcoming != null && upcoming.duration.inWholeHours < IMMINENT_THRESHOLD_HOURS

  val sortedIntervals =
    spot.timeline.sortedWith(compareBy({ it.days.minOrNull() }, { it.startTime })).map {
      // Permit holders are exempt from timed intervals, don't mark them active
      val isActive = it.isActiveAt(now) && !(isPermitSafe && isExemptForPermit(it))
      IntervalDisplay(it, isActive)
    }

  val sweepingDisplay =
    spot.sweepingSchedules.map { schedule ->
      val isActive = schedule.isWithinWindow(now)
      val nextOccurrence = schedule.nextOccurrence(now)
      val relativeText =
        if (isActive) null else nextOccurrence?.let { formatRelativeTime(it - now) }
      SweepingDisplay(schedule, isActive, relativeText)
    }

  return SpotDetailState(
    spot = spot,
    restrictionState = restrictionState,
    permitZone = permitZone,
    timelineSegments = segments,
    currentMinute = currentMinute,
    upcoming = upcoming,
    isImminent = isImminent,
    sortedIntervals = sortedIntervals,
    sweepingDisplay = sweepingDisplay,
  )
}

// ---------------------------------------------------------------------------
// Helpers (moved from SpotDetailContent)
// ---------------------------------------------------------------------------

private fun currentMinuteOfDay(now: Instant, zone: TimeZone): Int {
  val local = now.toLocalDateTime(zone)
  return local.hour * 60 + local.minute
}

private fun findNextEnforcement(
  spot: ParkingSpot,
  now: Instant,
  zone: TimeZone,
  isPermitSafe: Boolean = false,
): UpcomingEnforcement? {
  val local = now.toLocalDateTime(zone)
  val today = local.dayOfWeek
  val currentTime = local.time

  // Permit holders: only forbidden/restricted intervals matter, skip metered/limited
  val relevantTimeline =
    if (isPermitSafe) spot.timeline.filter { !isExemptForPermit(it) } else spot.timeline

  data class Candidate(
    val time: Instant,
    val timeLabel: String,
    val reason: String,
    val window: String,
  )

  val todayNext =
    relevantTimeline
      .filter { today in it.days && it.startTime > currentTime }
      .minByOrNull { it.startTime }

  val tomorrow = DayOfWeek.entries[(today.ordinal + 1) % 7]
  val tomorrowNext =
    if (todayNext == null)
      relevantTimeline.filter { tomorrow in it.days }.minByOrNull { it.startTime }
    else null

  val nextSweeping = spot.nextCleaning(now, zone)

  val candidates = buildList {
    todayNext?.let {
      val startInstant = instantFromLocalTime(it.startTime, local.date, zone)
      val reason = intervalReason(it.type)
      val window =
        "${formatTime(it.startTime)}-${formatTime(it.endTime)}, ${formatDayRange(it.days)}"
      add(Candidate(startInstant, formatTime(it.startTime), reason, window))
    }
    tomorrowNext?.let {
      val tomorrowDate = local.date.plus(1, DateTimeUnit.DAY)
      val startInstant = instantFromLocalTime(it.startTime, tomorrowDate, zone)
      val reason = intervalReason(it.type)
      val window =
        "${formatTime(it.startTime)}-${formatTime(it.endTime)}, ${formatDayRange(it.days)}"
      add(Candidate(startInstant, "${formatTime(it.startTime)} tomorrow", reason, window))
    }
    nextSweeping?.let { sweepInstant ->
      val sweepLocal = sweepInstant.toLocalDateTime(zone)
      val timeStr = DateTimeUtils.formatHour(sweepLocal.hour)
      val dayStr =
        if (sweepLocal.date == local.date) ""
        else if (sweepLocal.date == local.date.plus(1, DateTimeUnit.DAY)) " tomorrow"
        else " " + sweepLocal.dayOfWeek.shortName
      val schedule = spot.sweepingSchedules.sortedBy { it.nextOccurrence(now, zone) }.firstOrNull()
      val window =
        if (schedule != null) {
          val from = DateTimeUtils.formatHour(schedule.fromHour)
          val to = DateTimeUtils.formatHour(schedule.toHour)
          "$from-$to, ${schedule.weekday.name}"
        } else "$timeStr$dayStr"
      add(Candidate(sweepInstant, "$timeStr$dayStr", "Street cleaning", window))
    }
  }

  val nearest = candidates.minByOrNull { it.time } ?: return null
  val duration = nearest.time - now

  val label =
    if (duration.inWholeHours < 12) {
      "${nearest.timeLabel} (${formatRelativeTime(duration)})"
    } else {
      nearest.timeLabel
    }

  return UpcomingEnforcement(
    duration = duration,
    reason = nearest.reason,
    window = nearest.window,
    label = label,
  )
}

/** Metered and limited intervals are exempt for permit holders (they don't pay or have limits). */
private fun isExemptForPermit(interval: ParkingInterval): Boolean =
  interval.type is IntervalType.Metered || interval.type is IntervalType.Limited

private fun intervalReason(type: IntervalType): String =
  when (type) {
    is IntervalType.Forbidden -> type.reason.displayText()
    is IntervalType.Restricted -> type.reason.displayText()
    is IntervalType.Metered -> "Metered parking"
    is IntervalType.Limited -> "Time-limited parking"
    is IntervalType.Open -> "Enforcement"
  }

private fun instantFromLocalTime(time: LocalTime, date: LocalDate, zone: TimeZone): Instant =
  kotlinx.datetime.LocalDateTime(date, time).toInstant(zone)

private fun buildTodaySegments(
  spot: ParkingSpot,
  now: Instant,
  zone: TimeZone,
): List<TimelineSegment> {
  val today = now.toLocalDateTime(zone).dayOfWeek

  val segments = mutableListOf<TimelineSegment>()

  spot.timeline
    .filter { today in it.days }
    .forEach { interval ->
      val startMin = interval.startTime.hour * 60 + interval.startTime.minute
      val endMin = interval.endTime.hour * 60 + interval.endTime.minute
      val effectiveEnd = if (endMin == 0 || endMin <= startMin) 1440 else endMin
      segments.add(
        TimelineSegment(
          startMinute = startMin,
          endMinute = effectiveEnd,
          color = intervalColor(interval.type),
        )
      )
    }

  spot.sweepingSchedules
    .filter { it.weekday.toDayOfWeek() == today }
    .forEach { schedule ->
      segments.add(
        TimelineSegment(
          startMinute = schedule.fromHour * 60,
          endMinute = schedule.toHour * 60,
          color = Terracotta,
        )
      )
    }

  return segments.sortedBy { it.startMinute }
}
