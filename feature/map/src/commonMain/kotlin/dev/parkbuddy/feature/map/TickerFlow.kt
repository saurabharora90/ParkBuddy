package dev.parkbuddy.feature.map

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal fun tickerFlow(periodMs: Long = 30_000L): Flow<Instant> = flow {
  while (true) {
    emit(Clock.System.now())
    delay(periodMs)
  }
}
