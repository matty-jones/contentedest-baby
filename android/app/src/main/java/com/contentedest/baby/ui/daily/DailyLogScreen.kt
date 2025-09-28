package com.contentedest.baby.ui.daily

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import com.contentedest.baby.data.repo.EventRepository
import com.contentedest.baby.ui.events.SleepCreationScreen
import com.contentedest.baby.ui.events.FeedCreationScreen
import com.contentedest.baby.ui.events.NappyCreationScreen
import dagger.hilt.android.AndroidEntryPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLogScreen(
    vm: DailyLogViewModel,
    eventRepository: EventRepository,
    deviceId: String
) {
    val events by vm.events.collectAsState()
    val showUndoSnackbar by vm.showUndoSnackbar.collectAsState()
    var showEventCreation by remember { mutableStateOf(false) }
    var selectedEventType by remember { mutableStateOf<EventType?>(null) }
    var currentDate by remember { mutableStateOf(LocalDate.now()) }

    // Reload events when date changes
    LaunchedEffect(currentDate) {
        vm.load(currentDate)
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = { showEventCreation = true }
                ) {
                    Text("+")
                }

                // Event creation menu - simplified for now
                if (showEventCreation) {
                    // For now, just show a simple alert dialog
                    AlertDialog(
                        onDismissRequest = { showEventCreation = false },
                        title = { Text("Create Event") },
                        text = { Text("Choose event type:") },
                        confirmButton = {
                            Row {
                                TextButton(onClick = {
                                    selectedEventType = EventType.sleep
                                    showEventCreation = false
                                }) {
                                    Text("Sleep")
                                }
                                TextButton(onClick = {
                                    selectedEventType = EventType.feed
                                    showEventCreation = false
                                }) {
                                    Text("Feed")
                                }
                                TextButton(onClick = {
                                    selectedEventType = EventType.nappy
                                    showEventCreation = false
                                }) {
                                    Text("Nappy")
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEventCreation = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Date navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentDate = currentDate.minusDays(1) }) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                }

                Text(
                    text = currentDate.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                    style = MaterialTheme.typography.headlineSmall
                )

                IconButton(onClick = { currentDate = currentDate.plusDays(1) }) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next day")
                }
            }

            // Filter chips
            var selectedTypes by remember { mutableStateOf(setOf<EventType>()) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTypes.isEmpty() || EventType.sleep in selectedTypes,
                    onClick = {
                        selectedTypes = if (EventType.sleep in selectedTypes) {
                            selectedTypes - EventType.sleep
                        } else {
                            selectedTypes + EventType.sleep
                        }
                    },
                    label = { Text("Sleep") }
                )
                FilterChip(
                    selected = selectedTypes.isEmpty() || EventType.feed in selectedTypes,
                    onClick = {
                        selectedTypes = if (EventType.feed in selectedTypes) {
                            selectedTypes - EventType.feed
                        } else {
                            selectedTypes + EventType.feed
                        }
                    },
                    label = { Text("Feed") }
                )
                FilterChip(
                    selected = selectedTypes.isEmpty() || EventType.nappy in selectedTypes,
                    onClick = {
                        selectedTypes = if (EventType.nappy in selectedTypes) {
                            selectedTypes - EventType.nappy
                        } else {
                            selectedTypes + EventType.nappy
                        }
                    },
                    label = { Text("Nappy") }
                )
            }

            val filteredEvents = if (selectedTypes.isEmpty()) {
                events
            } else {
                events.filter { it.type in selectedTypes }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredEvents) { event ->
                    EventItem(
                        event = event,
                        onDelete = { vm.deleteEvent(event) },
                        onEdit = { /* TODO: Open edit dialog */ }
                    )
                }
            }

            if (showUndoSnackbar) {
                Snackbar(
                    action = {
                        TextButton(onClick = { vm.undoLastAction() }) {
                            Text("UNDO")
                        }
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Event deleted")
                }
            }
        }
    }

    // Event creation dialogs
    when (selectedEventType) {
        EventType.sleep -> {
            SleepCreationScreen(
                eventRepository = eventRepository,
                deviceId = deviceId,
                onDismiss = { selectedEventType = null },
                onEventCreated = { eventId ->
                    // Refresh the list after event creation
                    vm.load(currentDate)
                    selectedEventType = null
                }
            )
        }
        EventType.feed -> {
            FeedCreationScreen(
                eventRepository = eventRepository,
                deviceId = deviceId,
                onDismiss = { selectedEventType = null },
                onEventCreated = { eventId ->
                    // Refresh the list after event creation
                    vm.load(currentDate)
                    selectedEventType = null
                }
            )
        }
        EventType.nappy -> {
            NappyCreationScreen(
                eventRepository = eventRepository,
                deviceId = deviceId,
                onDismiss = { selectedEventType = null },
                onEventCreated = { eventId ->
                    // Refresh the list after event creation
                    vm.load(currentDate)
                    selectedEventType = null
                }
            )
        }
        null -> {} // No dialog to show
    }
}

@Composable
fun EventItem(
    event: EventEntity,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val backgroundColor = when (event.type) {
        EventType.sleep -> MaterialTheme.colorScheme.primaryContainer
        EventType.feed -> MaterialTheme.colorScheme.secondaryContainer
        EventType.nappy -> MaterialTheme.colorScheme.tertiaryContainer
    }

    val timeText = when (event.type) {
        EventType.sleep -> {
            val startTime = formatTime(event.start_ts ?: event.ts ?: 0)
            val endTime = if (event.end_ts != null) " - ${formatTime(event.end_ts)}" else " (ongoing)"
            "Sleep: $startTime$endTime"
        }
        EventType.feed -> {
            val time = formatTime(event.ts ?: event.start_ts ?: 0)
            val mode = event.feed_mode?.name?.replaceFirstChar { it.uppercase() } ?: "Unknown"
            val duration = if (event.end_ts != null && event.start_ts != null) {
                val durationSeconds = event.end_ts - event.start_ts
                val minutes = durationSeconds / 60
                val seconds = durationSeconds % 60
                if (minutes > 0) {
                    " (${minutes}m${if (seconds > 0) " ${seconds}s" else ""})"
                } else {
                    " (${seconds}s)"
                }
            } else ""
            "Feed ($mode): $time$duration"
        }
        EventType.nappy -> {
            val time = formatTime(event.ts ?: event.start_ts ?: 0)
            val type = event.nappy_type ?: "Unknown"
            "Nappy ($type): $time"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.type.name.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (event.note != null && event.note.isNotBlank()) {
                    Text(
                        text = event.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete event",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return "Unknown time"

    return try {
        val instant = Instant.ofEpochSecond(timestamp)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        "Invalid time"
    }
}


