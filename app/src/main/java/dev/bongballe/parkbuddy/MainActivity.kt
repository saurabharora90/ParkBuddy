package dev.bongballe.parkbuddy

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.bongballe.parkbuddy.data.repository.StreetCleaningRepository
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.parkbuddy.feature.map.MapScreen
import dev.parkbuddy.feature.map.MapViewModel
import dev.parkbuddy.feature.onboarding.permission.RequestPermissionScreen
import dev.parkbuddy.feature.reminders.watchlist.WatchlistScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.ActivityKey
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data object RouteRequestPermission

data object RouteBluetoothDeviceSelection

data object RouteMap

data object RouteWatchList

@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey(MainActivity::class)
@Inject
class MainActivity(
  private val viewModelFactory: MetroViewModelFactory,
  private val repository: StreetCleaningRepository,
  private val preferencesRepository: PreferencesRepository,
) : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    lifecycleScope.launch {
      if (!preferencesRepository.isInitialSyncDone.first()) {
        repository.refreshData()
        preferencesRepository.setInitialSyncDone(true)
      }
    }

    setContent {
      CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
        ParkBuddyTheme {
          val mapViewModel = metroViewModel<MapViewModel>()

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
                    }
                  )
                }
                entry<RouteMap> {
                  MapScreen(
                    viewModel = mapViewModel,
                    onNavigateToWatchlist = { backStack.add(RouteWatchList) },
                  )
                }
                entry<RouteWatchList> { WatchlistScreen() }
                entry<RouteBluetoothDeviceSelection> {
                  dev.parkbuddy.feature.onboarding.bluetooth.BluetoothDeviceSelectionScreen(
                    onDeviceSelected = {
                      backStack.clear()
                      backStack.add(RouteMap)
                    }
                  )
                }
              },
          )
        }
      }
    }
  }
}
