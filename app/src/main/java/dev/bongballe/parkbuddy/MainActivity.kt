package dev.bongballe.parkbuddy

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import dev.bongballe.parkbuddy.core.navigation.BluetoothDeviceSelectionRoute
import dev.bongballe.parkbuddy.core.navigation.BottomSheetSceneStrategy
import dev.bongballe.parkbuddy.core.navigation.LocalResultEventBus
import dev.bongballe.parkbuddy.core.navigation.MainRoute
import dev.bongballe.parkbuddy.core.navigation.NavEntryItem
import dev.bongballe.parkbuddy.core.navigation.OnboardingRoute
import dev.bongballe.parkbuddy.core.navigation.ResultEffect
import dev.bongballe.parkbuddy.core.navigation.ResultEventBus
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.parkbuddy.composeapp.MainScreen
import dev.parkbuddy.composeapp.MainViewModel
import dev.parkbuddy.composeapp.NavigatorImpl
import dev.parkbuddy.feature.map.PermissionChecker
import dev.parkbuddy.feature.settings.SettingsScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.ActivityKey
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel

@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey(MainActivity::class)
@Inject
class MainActivity(
  private val viewModelFactory: MetroViewModelFactory,
  private val entryBuilders: Set<@JvmSuppressWildcards NavEntryItem>,
  private val navigator: NavigatorImpl,
) : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      val resultBus = remember { ResultEventBus() }

      CompositionLocalProvider(
        LocalMetroViewModelFactory provides viewModelFactory,
        LocalResultEventBus provides resultBus,
      ) {
        val vm: MainViewModel = metroViewModel()
        val state by vm.stateFlow.collectAsStateWithLifecycle()

        ParkBuddyTheme {
          NavDisplay(
            backStack = navigator.backStack,
            onBack = { navigator.goBack() },
            sceneStrategy =
              BottomSheetSceneStrategy<NavKey>() then
                SinglePaneSceneStrategy(),
            entryDecorators =
              listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
              ),
            entryProvider = entryProvider {
              entryBuilders.forEach { builder -> this.builder() }
              entry<MainRoute> { key ->
                if (state != null) {
                  MainScreen(
                    isSyncing = state is MainViewModel.State.Loading,
                    selectedTab = key.tab,
                    navigator = navigator,
                    settingScreenContent = {
                      SettingsScreen(navigator = navigator)
                    },
                  )
                }
              }
            },
          )
        }

        ResultEffect<OnboardingRoute> { result ->
          if (result is OnboardingRoute.Complete) {
            val hasBluetooth =
              PermissionChecker.areBluetoothPermissionsGranted(this@MainActivity)
            if (hasBluetooth)
              navigator.goTo(BluetoothDeviceSelectionRoute)
          }
        }
      }
    }
  }
}
