package com.contentedest.baby.ui.daily

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLogScreen(vm: DailyLogViewModel) {
    val events by vm.events.collectAsState()
    val showUndoSnackbar by vm.showUndoSnackbar.collectAsState()

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(onClick = { /* TODO: Quick actions menu */ }) {
                    Text("+")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(events) { event ->
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
}

@Composable
fun EventItem(
    event: EventEntity,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = event.type.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatTimestamp(event.start_ts ?: event.ts),
                    style = MaterialTheme.typography.bodySmall
                )
                if (event.note != null) {
                    Text(
                        text = event.note,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                    contentDescription = "Delete event"
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long?): String {
    if (timestamp == null) return "Unknown time"
    // Simple formatting - could be improved with proper date formatting
    return "Time: $timestamp"
}


