package com.contentedest.baby.ui.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.contentedest.baby.data.local.EventType
import com.contentedest.baby.data.repo.EventRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCreationScreen(
    eventType: EventType,
    eventRepository: EventRepository,
    deviceId: String,
    onDismiss: () -> Unit,
    onEventCreated: (String) -> Unit
) {
    var note by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Create ${eventType.name.lowercase().replaceFirstChar { it.uppercase() }} Event",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    val now = Instant.now().epochSecond
                                    val eventId = when (eventType) {
                                        EventType.sleep -> eventRepository.createSleep(now, deviceId, note.takeIf { it.isNotBlank() })
                                        EventType.feed -> eventRepository.startFeed(now, deviceId, com.contentedest.baby.data.local.FeedMode.breast, note.takeIf { it.isNotBlank() })
                                        EventType.nappy -> eventRepository.createNappy(now, deviceId, "wet", note.takeIf { it.isNotBlank() })
                                    }
                                    onEventCreated(eventId)
                                } catch (e: Exception) {
                                    // TODO: Show error message
                                    println("Error creating event: $e")
                                } finally {
                                    isLoading = false
                                    onDismiss()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Create",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create")
                        }
                    }
                }
            }
        }
    }
}
