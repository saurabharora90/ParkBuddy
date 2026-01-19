package dev.bongballe.parkbuddy.model

import kotlinx.serialization.Serializable

@Serializable
data class Geometry(val type: String, val coordinates: List<List<Double>>)
