package dev.bongballe.parkbuddy.model

/**
 * Day of week for street sweeping schedules.
 *
 * [Holiday] is a special value used when sweeping occurs on holidays regardless of day.
 */
enum class Weekday {
  Mon,
  Tues,
  Wed,
  Thu,
  Fri,
  Sat,
  Sun,
  Holiday,
}
