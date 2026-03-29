package dev.bongballe.parkbuddy.model

import kotlin.jvm.JvmInline

@JvmInline
value class ReminderMinutes(val value: Int) : Comparable<ReminderMinutes> {
  init {
    require(value > 0) { "Reminder minutes must be positive" }
  }

  override fun compareTo(other: ReminderMinutes): Int = value.compareTo(other.value)
}
