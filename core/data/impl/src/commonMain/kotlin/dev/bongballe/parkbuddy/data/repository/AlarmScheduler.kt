package dev.bongballe.parkbuddy.data.repository

import kotlin.time.Instant

interface AlarmScheduler {
  fun setAlarm(index: Int, triggerAt: Instant, spotName: String, spotId: String, message: String)

  fun cancelAll()

  companion object {
    const val MAX_REMINDERS = 100
  }
}
