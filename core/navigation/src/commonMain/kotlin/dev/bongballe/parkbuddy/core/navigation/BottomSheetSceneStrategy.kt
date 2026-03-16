package dev.bongballe.parkbuddy.core.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

const val BOTTOM_SHEET_METADATA_KEY = "bottomSheet"

fun bottomSheetMetadata(): Map<String, Any> = mapOf(BOTTOM_SHEET_METADATA_KEY to true)

@Immutable
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
  override fun SceneStrategyScope<T>.calculateScene(
    entries: List<NavEntry<T>>
  ): BottomSheetScene<T>? {
    val lastEntry = entries.lastOrNull() ?: return null
    if (lastEntry.metadata[BOTTOM_SHEET_METADATA_KEY] != true) return null
    return BottomSheetScene(
      key = lastEntry.contentKey,
      previousEntries = entries.dropLast(1),
      overlaidEntries = entries.dropLast(1),
      entry = lastEntry,
      onBack = onBack,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetScene<T : Any>(
  override val key: Any,
  override val previousEntries: List<NavEntry<T>>,
  override val overlaidEntries: List<NavEntry<T>>,
  private val entry: NavEntry<T>,
  private val onBack: () -> Unit,
) : OverlayScene<T> {
  override val entries: List<NavEntry<T>> = listOf(entry)
  override val content: @Composable () -> Unit = {
    ModalBottomSheet(onDismissRequest = onBack) { entry.Content() }
  }
}
