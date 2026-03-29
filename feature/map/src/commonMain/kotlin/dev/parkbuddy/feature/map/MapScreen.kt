package dev.parkbuddy.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.bongballe.parkbuddy.core.navigation.Navigator
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlin.time.Clock

@Composable
expect fun MapScreen(
  navigator: Navigator,
  modifier: Modifier = Modifier,
  viewModel: MapViewModel = metroViewModel(),
  clock: Clock = Clock.System,
)
