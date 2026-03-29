package dev.bongballe.parkbuddy.util

import kotlinx.datetime.DayOfWeek

/** Three-letter title-case abbreviation (e.g. "Mon", "Tue"). */
val DayOfWeek.shortName: String
  get() = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

/** Full title-case name (e.g. "Monday", "Tuesday"). */
val DayOfWeek.displayName: String
  get() = name.lowercase().replaceFirstChar { it.uppercase() }
