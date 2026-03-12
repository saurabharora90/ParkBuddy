package dev.bongballe.parkbuddy

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import dev.bongballe.parkbuddy.core.navigation.MainRoute
import dev.bongballe.parkbuddy.core.navigation.Navigator
import dev.bongballe.parkbuddy.core.navigation.OnboardingRoute
import dev.bongballe.parkbuddy.data.repository.PreferencesRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class NavigatorImpl(
  private val preferencesRepository: PreferencesRepository,
) : Navigator {

  internal val backStack: SnapshotStateList<NavKey> =
    mutableStateListOf(MainRoute(tab = MainRoute.Tab.MAP))

  init {
    val hasSeenOnboarding = runBlocking { preferencesRepository.hasSeenOnboarding.first() }
    if (!hasSeenOnboarding)
      backStack.add(OnboardingRoute)
  }

  override fun goTo(destination: NavKey) {
    if (destination is MainRoute) {
      backStack.clear()
    }
    backStack.add(destination)
  }

  override fun goBack() {
    backStack.removeLastOrNull()
  }
}
