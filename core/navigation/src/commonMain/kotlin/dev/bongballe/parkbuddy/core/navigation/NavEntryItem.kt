package dev.bongballe.parkbuddy.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

typealias NavEntryItem = EntryProviderScope<NavKey>.() -> Unit
