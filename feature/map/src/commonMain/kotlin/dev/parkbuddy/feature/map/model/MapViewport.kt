package dev.parkbuddy.feature.map.model

/** Platform-agnostic map viewport state. */
data class MapViewport(val bounds: GeoBounds, val zoom: Float)
