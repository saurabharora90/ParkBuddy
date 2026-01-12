package dev.parkbuddy.feature.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.data.repository.StreetCleaningRepository
import dev.bongballe.parkbuddy.model.StreetCleaningSegmentModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class)
@ViewModelKey(WatchlistViewModel::class)
@Inject
class WatchlistViewModel(private val repository: StreetCleaningRepository) : ViewModel() {

  val watchedSegments: StateFlow<List<StreetCleaningSegmentModel>> =
    repository
      .getWatchedSegments()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  fun unwatch(segment: StreetCleaningSegmentModel) {
    viewModelScope.launch { repository.setWatchStatus(segment.id, false) }
  }
}

@Composable
fun WatchlistScreen(viewModel: WatchlistViewModel = metroViewModel()) {
  val watchedSegments by viewModel.watchedSegments.collectAsState()

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      Text(
        text = "Watchlist",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(16.dp),
      )
    },
  ) { innerPadding ->
    if (watchedSegments.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
      ) {
        Text("No streets watched yet.")
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(watchedSegments) { segment ->
          WatchlistItem(segment = segment, onUnwatch = { viewModel.unwatch(segment) })
        }
      }
    }
  }
}

@Composable
fun WatchlistItem(segment: StreetCleaningSegmentModel, onUnwatch: () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.padding(16.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(text = "Schedule: ${segment.schedule}", style = MaterialTheme.typography.bodyLarge)
      }
      IconButton(onClick = onUnwatch) {
        Icon(imageVector = Icons.Default.Delete, contentDescription = "Unwatch")
      }
    }
  }
}
