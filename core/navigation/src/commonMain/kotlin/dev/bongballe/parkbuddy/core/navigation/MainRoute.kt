package dev.bongballe.parkbuddy.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class MainRoute(val tab: Tab) : NavKey {
  enum class Tab {
    MAP,
    MY_ZONE,
    ACCOUNT,
  }
}
