package dev.bongballe.parkbuddy

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.parkbuddy.feature.map.MapScreen
import dev.parkbuddy.feature.map.MapViewModel
import dev.parkbuddy.feature.reminders.CleaningReminderWorker
import dev.parkbuddy.feature.reminders.WatchlistScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.ActivityKey
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel
import java.util.concurrent.TimeUnit

data object RouteMap

data object RouteWatchList

@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey(MainActivity::class)
@Inject
class MainActivity(private val viewModelFactory: MetroViewModelFactory) : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Schedule the worker (simplified for MVP)
    val workRequest = PeriodicWorkRequestBuilder<CleaningReminderWorker>(1, TimeUnit.DAYS).build()
    WorkManager.getInstance(this).enqueue(workRequest)

    setContent {
      CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
        ParkBuddyTheme {
          val context = LocalContext.current

          // Notification Permission Logic
          var hasNotificationPermission by remember {
            mutableStateOf(
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                  context,
                  Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
              } else {
                true
              }
            )
          }

          val permissionLauncher =
            rememberLauncherForActivityResult(
              contract = ActivityResultContracts.RequestPermission(),
              onResult = { isGranted -> hasNotificationPermission = isGranted },
            )

          LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              if (!hasNotificationPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            }
          }

          val mapViewModel = metroViewModel<MapViewModel>()

          val backStack = remember { mutableStateListOf<Any>(RouteMap) }
          Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
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
                  entry<RouteMap> {
                    MapScreen(
                      viewModel = mapViewModel,
                      onNavigateToWatchlist = { backStack.add(RouteWatchList) },
                    )
                  }
                  entry<RouteWatchList> { WatchlistScreen() }
                },
              modifier = Modifier.padding(innerPadding),
            )
          }
        }
      }
    }
  }
}
