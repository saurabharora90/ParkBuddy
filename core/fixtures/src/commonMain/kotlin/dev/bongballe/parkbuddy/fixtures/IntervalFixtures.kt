package dev.bongballe.parkbuddy.fixtures

import androidx.annotation.VisibleForTesting
import dev.bongballe.parkbuddy.model.IntervalSource
import dev.bongballe.parkbuddy.model.IntervalType
import dev.bongballe.parkbuddy.model.ParkingInterval
import dev.bongballe.parkbuddy.model.ProhibitionReason
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

@VisibleForTesting
fun limitedInterval(
  limitMinutes: Int,
  days: Set<DayOfWeek>,
  start: LocalTime,
  end: LocalTime,
  permitZones: List<String> = emptyList(),
) =
  ParkingInterval(
    type = IntervalType.Limited(limitMinutes),
    days = days,
    startTime = start,
    endTime = end,
    exemptPermitZones = permitZones,
    source = IntervalSource.REGULATION,
  )

@VisibleForTesting
fun meteredInterval(limitMinutes: Int, days: Set<DayOfWeek>, start: LocalTime, end: LocalTime) =
  ParkingInterval(
    type = IntervalType.Metered(limitMinutes),
    days = days,
    startTime = start,
    endTime = end,
    source = IntervalSource.METER,
  )

@VisibleForTesting
fun towInterval(days: Set<DayOfWeek>, start: LocalTime, end: LocalTime) =
  ParkingInterval(
    type = IntervalType.Forbidden(ProhibitionReason.TOW_AWAY),
    days = days,
    startTime = start,
    endTime = end,
    source = IntervalSource.TOW,
  )

@VisibleForTesting
fun forbiddenInterval(
  reason: ProhibitionReason,
  days: Set<DayOfWeek>,
  start: LocalTime,
  end: LocalTime,
) =
  ParkingInterval(
    type = IntervalType.Forbidden(reason),
    days = days,
    startTime = start,
    endTime = end,
    source = IntervalSource.REGULATION,
  )

@VisibleForTesting
fun restrictedInterval(
  reason: ProhibitionReason,
  days: Set<DayOfWeek>,
  start: LocalTime,
  end: LocalTime,
  permitZones: List<String> = emptyList(),
) =
  ParkingInterval(
    type = IntervalType.Restricted(reason),
    days = days,
    startTime = start,
    endTime = end,
    exemptPermitZones = permitZones,
    source = IntervalSource.REGULATION,
  )
