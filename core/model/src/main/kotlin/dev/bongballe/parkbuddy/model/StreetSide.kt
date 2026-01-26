package dev.bongballe.parkbuddy.model

/**
 * Represents which side of the street a parking spot or sweeping schedule applies to.
 *
 * This is important because opposite sides of the same street often have different
 * sweeping schedules (e.g., north side on Monday, south side on Tuesday).
 *
 * The left/right designation is relative to the direction of the street centerline
 * geometry, not compass directions.
 */
enum class StreetSide {
  LEFT,
  RIGHT;
}
