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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import dev.bongballe.parkbuddy.data.repository.ParkingRepository
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.parkbuddy.feature.map.MapScreen
import dev.parkbuddy.feature.onboarding.PermissionChecker
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
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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

    setContent {
      CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
        val vm: MainActivityViewModel = metroViewModel()
        val state by vm.stateFlow.collectAsStateWithLifecycle()

        ParkBuddyTheme {
          val bluetoothDeviceAddress = runBlocking {
            preferencesRepository.bluetoothDeviceAddress.first()
          }
          val userRppZone = runBlocking { repository.getUserPermitZone().first() }
          val initialRoute =
            when {
              !PermissionChecker.areAllPermissionsGranted(this@MainActivity) ->
                RouteRequestPermission

              bluetoothDeviceAddress == null -> RouteBluetoothDeviceSelection
              else -> Main
            }

          var mainPageSelectedTab by rememberSaveable {
            mutableIntStateOf(if (userRppZone == null) 1 else 0)
          }

          val backStack = remember(initialRoute) { mutableStateListOf(initialRoute) }
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
                entry<Main> {
                  if (state != null) {
                    MainScreen(
                      isSyncing = state is MainActivityViewModel.State.Loading,
                      selectedTab = mainPageSelectedTab,
                      onTabSelected = { mainPageSelectedTab = it },
                      onNavigateToBluetooth = { backStack.add(RouteBluetoothDeviceSelection) },
                    )
                  }
                }
                entry<RouteBluetoothDeviceSelection> {
                  BluetoothDeviceSelectionScreen(
                    onDeviceSelected = {
                      backStack.clear()
                      backStack.add(Main)
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

@Composable
private fun MainScreen(
  isSyncing: Boolean,
  selectedTab: Int,
  onTabSelected: (Int) -> Unit,
  onNavigateToBluetooth: () -> Unit,
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
            icon = { Icon(imageVector = Icons.Default.Map, contentDescription = null) },
            label = { Text("MAP") },
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
          )

          NavigationBarItem(
            icon = { Icon(imageVector = Icons.Default.Visibility, contentDescription = null) },
            label = { Text("WATCHED") },
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
          )

          NavigationBarItem(
            icon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) },
            label = { Text("ACCOUNT") },
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
          )
        }
      },
    ) { paddingValues ->
      Box(modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues)) {
        when (selectedTab) {
          0 -> MapScreen()
          1 -> WatchlistScreen()
          2 -> SettingsScreen(onNavigateToBluetooth = onNavigateToBluetooth)
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
