package dev.parkbuddy.feature.onboarding.di

import dev.bongballe.parkbuddy.core.navigation.BluetoothDeviceSelectionRoute
import dev.bongballe.parkbuddy.core.navigation.NavEntryItem
import dev.bongballe.parkbuddy.core.navigation.Navigator
import dev.bongballe.parkbuddy.core.navigation.OnboardingRoute
import dev.parkbuddy.feature.onboarding.bluetooth.BluetoothDeviceSelectionScreen
import dev.parkbuddy.feature.onboarding.setup.SetupChecklistScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface OnboardingEntryProvider {

  @Provides
  @IntoSet
  fun provideOnboardingRouteEntry(navigator: Navigator): NavEntryItem = {
    entry<OnboardingRoute> { key -> SetupChecklistScreen(navigator = navigator) }
    entry<BluetoothDeviceSelectionRoute> { BluetoothDeviceSelectionScreen(navigator = navigator) }
  }
}
