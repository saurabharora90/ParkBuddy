package dev.bongballe.parkbuddy.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data class BluetoothDeviceSelectionRoute(val isFromOnboarding: Boolean) : NavKey
