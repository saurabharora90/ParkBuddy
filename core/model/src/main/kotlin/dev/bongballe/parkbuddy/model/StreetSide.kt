package dev.bongballe.parkbuddy.model

enum class StreetSide {
  LEFT,
  RIGHT;

  companion object {
    fun fromApiValue(value: String): StreetSide = when (value.uppercase()) {
      "L" -> LEFT
      "R" -> RIGHT
      else -> LEFT
    }
  }
}
