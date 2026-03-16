package dev.parkbuddy.feature.map

import dev.bongballe.parkbuddy.core.navigation.NavEntryItem
import dev.bongballe.parkbuddy.core.navigation.Navigator
import dev.bongballe.parkbuddy.core.navigation.SpotDetailRoute
import dev.bongballe.parkbuddy.core.navigation.bottomSheetMetadata
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@ContributesTo(AppScope::class)
interface MapEntryProvider {

  @Provides
  @IntoSet
  fun provideSpotDetailEntry(navigator: Navigator): NavEntryItem = {
    entry<SpotDetailRoute>(metadata = bottomSheetMetadata()) { key ->
      val vm =
        assistedMetroViewModel<SpotDetailViewModel, SpotDetailViewModel.Factory> {
          create(key.spot, key.permitZone)
        }
      SpotDetailContent(viewModel = vm, navigator = navigator)
    }
  }
}
