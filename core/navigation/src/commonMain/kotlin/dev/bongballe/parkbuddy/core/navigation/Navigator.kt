package dev.bongballe.parkbuddy.core.navigation

import androidx.navigation3.runtime.NavKey

interface Navigator {

  fun goTo(destination: NavKey)

  fun goBack()
}
