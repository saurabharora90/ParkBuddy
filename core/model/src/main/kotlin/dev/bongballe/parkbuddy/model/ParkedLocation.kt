package dev.bongballe.parkbuddy.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ParkedLocation(
  val spotId: String,
  val location: Location,
  val parkedAt: Instant,
)
