package dev.parkbuddy.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.bongballe.parkbuddy.core.navigation.Navigator
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
expect fun MapScreen(
  modifier: Modifier = Modifier,
  navigator: Navigator,
  viewModel: MapViewModel = metroViewModel(),
)
