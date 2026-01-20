package dev.parkbuddy.feature.reminders.watchlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditRoad
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Signpost
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.bongballe.parkbuddy.model.Geometry
import dev.bongballe.parkbuddy.model.StreetCleaningSegmentModel
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme
import dev.bongballe.parkbuddy.theme.Terracotta
import dev.parkbuddy.core.ui.SquircleIcon
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun WatchlistScreen(viewModel: WatchlistViewModel = metroViewModel()) {
  val watchedSegments by viewModel.watchedSegments.collectAsState()
  val reminders by viewModel.reminders.collectAsState()
  val searchQuery by viewModel.searchQuery.collectAsState()
  val isSearchActive by viewModel.isSearchActive.collectAsState()
  val searchResults by viewModel.searchResults.collectAsState()

  WatchlistContent(
    watchedSegments = watchedSegments,
    reminders = reminders,
    searchQuery = searchQuery,
    isSearchActive = isSearchActive,
    searchResults = searchResults,
    onSearchQueryChanged = viewModel::onSearchQueryChanged,
    onSearchActiveChanged = viewModel::onSearchActiveChanged,
    onWatch = viewModel::watch,
    onUnwatch = viewModel::unwatch,
    onAddReminder = viewModel::addReminder,
    onRemoveReminder = viewModel::removeReminder,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistContent(
  watchedSegments: List<StreetCleaningSegmentModel>,
  reminders: List<Int>,
  searchQuery: String,
  isSearchActive: Boolean,
  searchResults: List<StreetCleaningSegmentModel>,
  onSearchQueryChanged: (String) -> Unit,
  onSearchActiveChanged: (Boolean) -> Unit,
  onWatch: (StreetCleaningSegmentModel) -> Unit,
  onUnwatch: (StreetCleaningSegmentModel) -> Unit,
  onAddReminder: (Int, Int) -> Unit,
  onRemoveReminder: (Int) -> Unit,
) {
  var showAddReminderDialog by remember { mutableStateOf(false) }

  if (showAddReminderDialog) {
    AddReminderDialog(
      onDismiss = { showAddReminderDialog = false },
      onConfirm = { h, m ->
        onAddReminder(h, m)
        showAddReminderDialog = false
      },
    )
  }

  Scaffold(
    topBar = {
      if (isSearchActive) {
        @Suppress("DEPRECATION")
        SearchBar(
          query = searchQuery,
          onQueryChange = onSearchQueryChanged,
          onSearch = { onSearchActiveChanged(false) },
          active = true,
          onActiveChange = onSearchActiveChanged,
          placeholder = { Text("Search streets...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchQueryChanged("") }) {
                Icon(Icons.Default.Close, contentDescription = "Clear")
              }
            } else {
              IconButton(onClick = { onSearchActiveChanged(false) }) {
                Icon(Icons.Default.Close, contentDescription = "Close Search")
              }
            }
          },
        ) {
          LazyColumn {
            items(searchResults) { segment ->
              ListItem(
                headlineContent = { Text(segment.streetName) },
                supportingContent = { Text(segment.schedule) },
                modifier = Modifier.clickable { onWatch(segment) },
              )
            }
          }
        }
      } else {
        TopAppBar(
          title = { Text(text = "Watched Streets & Rules", modifier = Modifier.fillMaxWidth()) },
          actions = {
            IconButton(onClick = { onSearchActiveChanged(true) }) {
              Icon(Icons.Default.Search, contentDescription = "Search")
            }
          },
        )
      }
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    if (watchedSegments.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier.fillMaxWidth(),
        ) {
          SquircleIcon(icon = Icons.Default.Signpost, contentDescription = null, size = 96.dp)

          Spacer(modifier = Modifier.height(24.dp))

          Text(
            text = "No Watched Streets Yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = "Add the streets where you regularly park to get automatic cleaning reminders.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
          )

          Spacer(modifier = Modifier.height(32.dp))

          Button(
            onClick = { onSearchActiveChanged(true) },
            shape = RoundedCornerShape(50),
            modifier = Modifier.height(56.dp).padding(horizontal = 24.dp),
            contentPadding = PaddingValues(horizontal = 32.dp),
          ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Your First Street", fontWeight = FontWeight.Bold)
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          Text(
            "Watched Streets",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
          )
        }
        items(watchedSegments) { segment ->
          WatchlistItem(segment = segment, onUnwatch = { onUnwatch(segment) })
        }

        item {
          Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              "Reminders",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.titleMedium,
            )
            if (reminders.size < 5) {
              IconButton(onClick = { showAddReminderDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Reminder")
              }
            }
          }
        }

        if (reminders.isEmpty()) {
          item { Text("No reminders set.", style = MaterialTheme.typography.bodyMedium) }
        } else {
          items(reminders) { minutes ->
            ReminderItem(minutes = minutes, onDelete = { onRemoveReminder(minutes) })
          }
        }
      }
    }
  }
}

@Composable
fun WatchlistItem(segment: StreetCleaningSegmentModel, onUnwatch: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
  ) {
    Row(
      modifier = Modifier.padding(16.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SquircleIcon(icon = Icons.Default.EditRoad, contentDescription = null, size = 48.dp)
      Spacer(modifier = Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = segment.streetName,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = segment.schedule,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(onClick = onUnwatch) {
        Icon(imageVector = Icons.Default.Delete, contentDescription = "Unwatch", tint = Terracotta)
      }
    }
  }
}

@Composable
fun ReminderItem(minutes: Int, onDelete: () -> Unit) {
  val hours = minutes / 60
  val mins = minutes % 60
  val timeString = if (hours > 0) "$hours hr ${mins} min before" else "$mins min before"

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    border =
      BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
  ) {
    Row(
      modifier = Modifier.padding(16.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SquircleIcon(
        icon = Icons.Default.NotificationsActive,
        contentDescription = null,
        size = 48.dp,
        shape = RoundedCornerShape(16.dp),
        iconTint = Color.White,
        backgroundTint = MaterialTheme.colorScheme.primary,
      )
      Spacer(modifier = Modifier.width(16.dp))
      Text(
        text = timeString,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onDelete) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "Delete Reminder",
          tint = Terracotta,
        )
      }
    }
  }
}

@Composable
fun AddReminderDialog(onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
  var hours by remember { mutableStateOf("") }
  var minutes by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Reminder") },
    text = {
      Column {
        Text("Notify me before cleaning:")
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = hours,
            onValueChange = { if (it.all { char -> char.isDigit() }) hours = it },
            label = { Text("Hours") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
          Spacer(modifier = Modifier.width(8.dp))
          OutlinedTextField(
            value = minutes,
            onValueChange = { if (it.all { char -> char.isDigit() }) minutes = it },
            label = { Text("Minutes") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val h = hours.toIntOrNull() ?: 0
          val m = minutes.toIntOrNull() ?: 0
          if (h > 0 || m > 0) {
            onConfirm(h, m)
          }
        }
      ) {
        Text("Add")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Preview(showBackground = true)
@Composable
private fun WatchlistScreenPreview_Empty() {
  ParkBuddyTheme {
    WatchlistContent(
      watchedSegments = emptyList(),
      reminders = emptyList(),
      searchQuery = "",
      isSearchActive = false,
      searchResults = emptyList(),
      onSearchQueryChanged = {},
      onSearchActiveChanged = {},
      onWatch = {},
      onUnwatch = {},
      onAddReminder = { _, _ -> },
      onRemoveReminder = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun WatchlistScreenPreview_Populated() {
  val mockSegments =
    listOf(
      StreetCleaningSegmentModel(
        id = 1,
        streetName = "Mission St",
        schedule = "Mon, Wed, Fri 12:00-14:00",
        locationData = Geometry("MultiLineString", emptyList()),
        isWatched = true,
        weeks = listOf(true, true, true, true, true),
        servicedOnHolidays = false,
      ),
      StreetCleaningSegmentModel(
        id = 2,
        streetName = "Valencia St",
        schedule = "Tue, Thu 08:00-10:00",
        locationData = Geometry("MultiLineString", emptyList()),
        isWatched = true,
        weeks = listOf(true, true, true, true, true),
        servicedOnHolidays = true,
      ),
    )
  ParkBuddyTheme {
    WatchlistContent(
      watchedSegments = mockSegments,
      reminders = listOf(24 * 60, 4 * 60),
      searchQuery = "",
      isSearchActive = false,
      searchResults = emptyList(),
      onSearchQueryChanged = {},
      onSearchActiveChanged = {},
      onWatch = {},
      onUnwatch = {},
      onAddReminder = { _, _ -> },
      onRemoveReminder = {},
    )
  }
}
