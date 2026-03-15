package dev.bongballe.parkbuddy.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute : NavKey {

  data object Complete : NavResult
}
