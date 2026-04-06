package dev.parkbuddy.composeapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.bongballe.parkbuddy.core.navigation.MainRoute
import dev.bongballe.parkbuddy.core.navigation.Navigator
import dev.parkbuddy.core.ui.ParkBuddyIcons
import dev.parkbuddy.feature.map.MapScreen
import dev.parkbuddy.feature.reminders.permitzone.PermitZoneScreen

@Composable
fun MainScreen(
  initialTab: MainRoute.Tab,
  navigator: Navigator,
  modifier: Modifier = Modifier,
  settingScreenContent: @Composable () -> Unit,
) {
  var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
  Scaffold(
    modifier = modifier,
    bottomBar = {
      NavigationBar(containerColor = Color.White) {
        NavigationBarItem(
          icon = { Icon(imageVector = ParkBuddyIcons.Map, contentDescription = null) },
          label = { Text("MAP") },
          selected = selectedTab == MainRoute.Tab.MAP,
          onClick = { selectedTab = MainRoute.Tab.MAP },
        )

        NavigationBarItem(
          icon = { Icon(imageVector = ParkBuddyIcons.Visibility, contentDescription = null) },
          label = { Text("MY ZONE") },
          selected = selectedTab == MainRoute.Tab.MY_ZONE,
          onClick = { selectedTab = MainRoute.Tab.MY_ZONE },
        )

        NavigationBarItem(
          icon = { Icon(imageVector = ParkBuddyIcons.Person, contentDescription = null) },
          label = { Text("ACCOUNT") },
          selected = selectedTab == MainRoute.Tab.ACCOUNT,
          onClick = { selectedTab = MainRoute.Tab.ACCOUNT },
        )
      }
    },
  ) { paddingValues ->
    Box(modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues)) {
      when (selectedTab) {
        MainRoute.Tab.MAP -> MapScreen(navigator = navigator)
        MainRoute.Tab.MY_ZONE -> PermitZoneScreen()
        MainRoute.Tab.ACCOUNT -> settingScreenContent()
      }
    }
  }
}
