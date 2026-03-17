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
import dev.bongballe.parkbuddy.model.ProhibitionReason
import dev.bongballe.parkbuddy.model.SweepingSchedule
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private const val IMMINENT_THRESHOLD_HOURS = 3L

data class SpotDetailState(
  val spot: ParkingSpot,
  val restrictionState: ParkingRestrictionState,
  val now: Instant,
  val permitZone: String?,
  val isPermitExempt: Boolean,
  val timelineSegments: List<TimelineSegment>,
  val currentMinute: Int,
  val upcoming: UpcomingEnforcementDisplay?,
  val sortedIntervals: List<IntervalDisplay>,
  val sweepingDisplay: List<SweepingDisplay>,
) {
  val isImminent: Boolean
    get() = upcoming != null && upcoming.duration.inWholeHours < IMMINENT_THRESHOLD_HOURS
}

data class IntervalDisplay(val interval: ParkingInterval, val isActive: Boolean)

data class SweepingDisplay(
  val schedule: SweepingSchedule,
  val isActive: Boolean,
  val relativeTimeText: String?,
)

data class UpcomingEnforcementDisplay(
  val duration: Duration,
  val reason: String,
  val window: String,
  val label: String,
)

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

  val isPermitExempt = restrictionState is ParkingRestrictionState.PermitSafe

  val currentMinute = currentMinuteOfDay(now, zone)
  val allSegments = buildTodaySegments(spot, now, zone)
  val segments =
    if (isPermitExempt)
      allSegments.filter { it.intervalType == null || it.intervalType?.isProhibited == true }
    else allSegments

  val upcoming = buildUpcomingDisplay(restrictionState, spot, now, zone)

  val sortedIntervals =
    spot.timeline.sortedWith(compareBy({ it.days.minOrNull() }, { it.startTime })).map {
      val isActive = it.isActiveAt(now) && !(isPermitExempt && isPermitExemptible(it))
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
    now = now,
    permitZone = permitZone,
    isPermitExempt = isPermitExempt,
    timelineSegments = segments,
    currentMinute = currentMinute,
    upcoming = upcoming,
    sortedIntervals = sortedIntervals,
    sweepingDisplay = sweepingDisplay,
  )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun isPermitExemptible(interval: ParkingInterval): Boolean =
  when (interval.type) {
    is IntervalType.Limited,
    is IntervalType.Metered -> true

    is IntervalType.Restricted ->
      (interval.type as IntervalType.Restricted).reason == ProhibitionReason.RESIDENTIAL_PERMIT

    else -> false
  }

private fun currentMinuteOfDay(now: Instant, zone: TimeZone): Int {
  val local = now.toLocalDateTime(zone)
  return local.hour * 60 + local.minute
}

/**
 * Builds the upcoming enforcement display from the [ParkingRestrictionState]. Uses state-specific
 * data (nextCleaning, forbidden startsAt, pending startsAt) to find the nearest upcoming event.
 */
private fun buildUpcomingDisplay(
  state: ParkingRestrictionState,
  spot: ParkingSpot,
  now: Instant,
  zone: TimeZone,
): UpcomingEnforcementDisplay? {
  val today = now.toLocalDateTime(zone).date

  fun formatEventLabel(instant: Instant): String {
    val eventLocal = instant.toLocalDateTime(zone)
    val timeStr = DateTimeUtils.formatHour(eventLocal.hour)
    val dayStr =
      if (eventLocal.date == today) ""
      else if (eventLocal.date == today.plus(1, DateTimeUnit.DAY)) " tomorrow"
      else " " + eventLocal.dayOfWeek.shortName
    return "$timeStr$dayStr"
  }

  data class Candidate(val time: Instant, val label: String, val reason: String, val window: String)

  val candidates = buildList {
    if (state is ParkingRestrictionState.ForbiddenUpcoming) {
      add(
        Candidate(state.startsAt, formatEventLabel(state.startsAt), state.reason.displayText(), "")
      )
    }

    if (state is ParkingRestrictionState.ActiveTimed) {
      val reason = if (state.paymentRequired) "Meter expires" else "Time limit expires"
      add(Candidate(state.expiry, formatEventLabel(state.expiry), reason, ""))
    }

    if (state is ParkingRestrictionState.PendingTimed) {
      val reason = if (state.paymentRequired) "Metered parking" else "Time-limited parking"
      add(Candidate(state.startsAt, formatEventLabel(state.startsAt), reason, ""))
    }

    state.nextCleaning?.let { cleaningInstant ->
      val schedule = spot.sweepingSchedules.sortedBy { it.nextOccurrence(now, zone) }.firstOrNull()
      val label = formatEventLabel(cleaningInstant)
      val window =
        if (schedule != null) {
          val from = DateTimeUtils.formatHour(schedule.fromHour)
          val to = DateTimeUtils.formatHour(schedule.toHour)
          "$from-$to, ${schedule.weekday.name}"
        } else label
      add(Candidate(cleaningInstant, label, "Street cleaning", window))
    }
  }

  val nearest = candidates.minByOrNull { it.time } ?: return null
  val duration = nearest.time - now
  if (duration.isNegative()) return null

  val label =
    if (duration.inWholeHours < 12) {
      "${nearest.label} (${formatRelativeTime(duration)})"
    } else {
      nearest.label
    }

  return UpcomingEnforcementDisplay(
    duration = duration,
    reason = nearest.reason,
    window = nearest.window,
    label = label,
  )
}

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
          intervalType = interval.type,
        )
      )
    }

  spot.sweepingSchedules
    .filter { it.weekday.toDayOfWeek() == today }
    .forEach { schedule ->
      segments.add(
        TimelineSegment(startMinute = schedule.fromHour * 60, endMinute = schedule.toHour * 60)
      )
    }

  return segments.sortedBy { it.startMinute }
}
