package dev.bongballe.parkbuddy

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.parkbuddy.feature.map.MapScreen
import dev.parkbuddy.feature.onboarding.bluetooth.BluetoothDeviceSelectionScreen
import dev.parkbuddy.feature.onboarding.permission.RequestPermissionScreen
import dev.parkbuddy.feature.reminders.watchlist.WatchlistScreen
import dev.parkbuddy.feature.settings.SettingsScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.ActivityKey
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data object RouteRequestPermission

data object RouteBluetoothDeviceSelection

data object Main

@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey(MainActivity::class)
@Inject
class MainActivity(
  private val viewModelFactory: MetroViewModelFactory,
  private val repository: ParkingRepository,
  private val preferencesRepository: PreferencesRepository,
) : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    lifecycleScope.launch {
      // Check if data needs refresh - either first sync or database was wiped
      val needsSync =
        !preferencesRepository.isInitialSyncDone.first() ||
          repository.getAllSpots().first().isEmpty()
      if (needsSync) {
        val didRefreshSucceed = repository.refreshData()
        if (didRefreshSucceed) preferencesRepository.setInitialSyncDone(true)
      }
    }

    setContent {
      CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
        ParkBuddyTheme {
          val backStack = remember { mutableStateListOf<Any>(RouteRequestPermission) }
          NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators =
              listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
              ),
            entryProvider =
              entryProvider {
                entry<RouteRequestPermission> {
                  RequestPermissionScreen(
                    onPermissionsGranted = {
                      backStack.clear()
                      backStack.add(RouteBluetoothDeviceSelection)
                    },
                  )
                }
                entry<Main> { MainScreen(backStack) }
                entry<RouteBluetoothDeviceSelection> {
                  BluetoothDeviceSelectionScreen(
                    onDeviceSelected = {
                      backStack.clear()
                      backStack.add(Main)
                    },
                  )
                }
              },
          )
        }
      }
    }
  }
}

@Composable
private fun MainScreen(backStack: MutableList<Any>, modifier: Modifier = Modifier) {
  var selectedItem by remember { mutableIntStateOf(0) }
  Scaffold(
    modifier = modifier,
    bottomBar = {
      NavigationBar(containerColor = Color.White) {
        NavigationBarItem(
          icon = { Icon(imageVector = Icons.Default.Map, contentDescription = null) },
          label = { Text("MAP") },
          selected = selectedItem == 0,
          onClick = { selectedItem = 0 },
        )

        NavigationBarItem(
          icon = { Icon(imageVector = Icons.Default.Visibility, contentDescription = null) },
          label = { Text("WATCHED") },
          selected = selectedItem == 1,
          onClick = { selectedItem = 1 },
        )

        NavigationBarItem(
          icon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) },
          label = { Text("ACCOUNT") },
          selected = selectedItem == 2,
          onClick = { selectedItem = 2 },
        )
      }
    },
  ) { paddingValues ->
    Box(modifier = Modifier
      .padding(paddingValues)
      .consumeWindowInsets(paddingValues)) {
      when (selectedItem) {
        0 -> MapScreen()
        1 -> WatchlistScreen()
        2 ->
          SettingsScreen(
            onNavigateToBluetooth = {
              backStack.clear()
              backStack.add(RouteBluetoothDeviceSelection)
            },
          )
      }
    }
  }
}
