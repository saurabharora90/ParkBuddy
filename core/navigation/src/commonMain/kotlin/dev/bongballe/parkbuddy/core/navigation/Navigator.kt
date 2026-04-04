package dev.bongballe.parkbuddy.core.navigation

import androidx.navigation3.runtime.NavKey

interface Navigator {

  fun goTo(destination: NavKey)

  fun goBack()

  /** Atomically replaces the entire backstack with [destination] as the new root. */
  fun resetRoot(destination: NavKey)
}
