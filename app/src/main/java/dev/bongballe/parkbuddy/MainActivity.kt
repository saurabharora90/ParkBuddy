package dev.bongballe.parkbuddy

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import dev.bongballe.parkbuddy.core.navigation.BluetoothDeviceSelectionRoute
import dev.bongballe.parkbuddy.core.navigation.LocalResultEventBus
import dev.bongballe.parkbuddy.core.navigation.MainRoute
import dev.bongballe.parkbuddy.core.navigation.NavEntryItem
import dev.bongballe.parkbuddy.core.navigation.Navigator
import dev.bongballe.parkbuddy.core.navigation.OnboardingRoute
import dev.bongballe.parkbuddy.core.navigation.ResultEffect
import dev.bongballe.parkbuddy.core.navigation.ResultEventBus
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.parkbuddy.core.ui.ParkBuddyIcons
import dev.parkbuddy.feature.map.MapScreen
import dev.parkbuddy.feature.map.PermissionChecker
import dev.parkbuddy.feature.reminders.permitzone.PermitZoneScreen
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
        val vm: MainActivityViewModel = metroViewModel()
        val state by vm.stateFlow.collectAsStateWithLifecycle()

        ParkBuddyTheme {
          NavDisplay(
            backStack = navigator.backStack,
            onBack = { navigator.goBack() },
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
                    isSyncing = state is MainActivityViewModel.State.Loading,
                    selectedTab = key.tab,
                    navigator = navigator,
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

@Composable
private fun MainScreen(
  isSyncing: Boolean,
  selectedTab: MainRoute.Tab,
  navigator: Navigator,
  modifier: Modifier = Modifier,
) {
  if (isSyncing) {
    SyncingScreen()
  } else {
    Scaffold(
      modifier = modifier,
      bottomBar = {
        NavigationBar(containerColor = Color.White) {
          NavigationBarItem(
            icon = { Icon(imageVector = ParkBuddyIcons.Map, contentDescription = null) },
            label = { Text("MAP") },
            selected = selectedTab == MainRoute.Tab.MAP,
            onClick = {
              navigator.goTo(MainRoute(MainRoute.Tab.MAP))
            },
          )

          NavigationBarItem(
            icon = { Icon(imageVector = ParkBuddyIcons.Visibility, contentDescription = null) },
            label = { Text("MY ZONE") },
            selected = selectedTab == MainRoute.Tab.MY_ZONE,
            onClick = {
              navigator.goTo(MainRoute(MainRoute.Tab.MY_ZONE))
            },
          )

          NavigationBarItem(
            icon = { Icon(imageVector = ParkBuddyIcons.Person, contentDescription = null) },
            label = { Text("ACCOUNT") },
            selected = selectedTab == MainRoute.Tab.ACCOUNT,
            onClick = {
              navigator.goTo(MainRoute(MainRoute.Tab.ACCOUNT))
            },
          )
        }
      },
    ) { paddingValues ->
      Box(
        modifier = Modifier
          .padding(paddingValues)
          .consumeWindowInsets(paddingValues),
      ) {
        when (selectedTab) {
          MainRoute.Tab.MAP -> MapScreen(navigator = navigator)
          MainRoute.Tab.MY_ZONE -> PermitZoneScreen()
          MainRoute.Tab.ACCOUNT -> SettingsScreen(navigator)
        }
      }
    }
  }
}

@Composable
private fun SyncingScreen() {
  val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.sync_loading))

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = Modifier.size(250.dp),
      )
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = "Syncing data for your city...",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "Hang tight, we're getting everything ready!",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
