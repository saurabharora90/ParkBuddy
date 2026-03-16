package dev.parkbuddy.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.bongballe.parkbuddy.core.navigation.Navigator

@Composable
actual fun MapScreen(modifier: Modifier, navigator: Navigator, viewModel: MapViewModel) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text("iOS Map placeholder")
  }
}
