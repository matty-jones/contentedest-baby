package com.contentedest.baby.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import com.contentedest.baby.data.repo.EventRepository
import com.contentedest.baby.domain.TimeRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TimelineViewModel(private val repo: EventRepository) {
    private val _events = MutableStateFlow<List<EventEntity>>(emptyList())
    val events: StateFlow<List<EventEntity>> = _events

    suspend fun load(date: LocalDate) {
        // Baby day runs from 7am to 7am (next day)
        val dayStart = date.atTime(7, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
        val dayEnd = date.plusDays(1).atTime(7, 0).atZone(ZoneId.systemDefault()).toEpochSecond() - 1
        _events.value = repo.eventsForDay(dayStart, dayEnd)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    vm: TimelineViewModel,
    eventRepository: EventRepository,
    date: LocalDate,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val events by vm.events.collectAsState()
    var currentDate by remember { mutableStateOf(date) }

    // Load events when date changes
    LaunchedEffect(currentDate) {
        vm.load(currentDate)
    }

    // UI State for details dialog
    var selectedEvent by remember { mutableStateOf<EventEntity?>(null) }

    // Define event colors
    val eventColors = mapOf(
        EventType.sleep to Color(0xFF87CEEB), // Sky blue
        EventType.feed to Color(0xFFFFB6C1), // Light pink
        EventType.nappy to Color(0xFF98FB98) // Pale green
    )

    Column(modifier = modifier.fillMaxSize()) {
        // Date header with navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<",
                modifier = Modifier.clickable { currentDate = currentDate.minusDays(1) },
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = formatDate(currentDate),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = ">",
                modifier = Modifier.clickable { currentDate = currentDate.plusDays(1) },
                style = MaterialTheme.typography.titleLarge
            )
        }

        // Vertical timeline with horizontal event bars
        val scrollState = rememberScrollState()
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            VerticalTimelineWithBars(
                events = events,
                currentDate = currentDate,
                eventColors = eventColors,
                onEventClick = { selectedEvent = it },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Details dialog with proper formatting
        if (selectedEvent != null) {
            EventDetailsDialog(
                event = selectedEvent!!,
                onDismiss = { selectedEvent = null }
            )
        }
    }
}

@Composable
fun VerticalTimelineWithBars(
    events: List<EventEntity>,
    currentDate: LocalDate,
    eventColors: Map<EventType, Color>,
    onEventClick: (EventEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedEvents = events.sortedBy { it.start_ts ?: it.ts ?: 0L }
    
    Column(modifier = modifier) {
        // Timeline header
        Text(
            text = "Timeline (7 AM - 7 AM)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Create hourly grid with event bars (7 AM to 6 AM next day)
        for (hour in 7..30) { // 7 AM to 6 AM next day (30 = 6 AM next day)
            val displayHour = if (hour >= 24) hour - 24 else hour
            val hourEvents = getEventsForHour(sortedEvents, displayHour, currentDate)
            
            // Hour label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatHour(displayHour),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(60.dp)
                )
                
                // Timeline bar area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .background(
                            Color.Gray.copy(alpha = 0.1f),
                            MaterialTheme.shapes.small
                        )
                ) {
                    // Draw hour markers
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        
                        // Draw vertical lines every 15 minutes (4 per hour)
                        for (i in 0..4) {
                            val x = (width / 4f) * i
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.3f),
                                start = Offset(x, 0f),
                                end = Offset(x, height),
                                strokeWidth = 1f
                            )
                        }
                    }
                    
                    // Draw event bars
                    hourEvents.forEach { event ->
                        EventBar(
                            event = event,
                            eventColors = eventColors,
                            onEventClick = onEventClick,
                            hourStart = getHourStart(displayHour, currentDate),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EventBar(
    event: EventEntity,
    eventColors: Map<EventType, Color>,
    onEventClick: (EventEntity) -> Unit,
    hourStart: Long,
    modifier: Modifier = Modifier
) {
    val eventStart = event.start_ts ?: event.ts ?: 0L
    val eventEnd = event.end_ts ?: (eventStart + 900L) // Default 15 minutes for point events
    
    // Calculate position within the hour
    val hourEnd = hourStart + 3600L
    val clampedStart = eventStart.coerceAtLeast(hourStart)
    val clampedEnd = eventEnd.coerceAtMost(hourEnd)
    
    if (clampedStart < clampedEnd) {
        val startFraction = (clampedStart - hourStart).toFloat() / 3600f
        val endFraction = (clampedEnd - hourStart).toFloat() / 3600f
        val widthFraction = endFraction - startFraction
        
        // Position the event bar within the hour
        BoxWithConstraints(
            modifier = modifier
                .fillMaxHeight()
                .fillMaxWidth()
        ) {
            val startOffset = maxWidth * startFraction
            val barWidth = maxWidth * widthFraction
            
            Box(
                modifier = Modifier
                    .offset(x = startOffset)
                    .width(barWidth)
                    .fillMaxHeight()
                    .background(
                        eventColors[event.type] ?: Color.Gray,
                        MaterialTheme.shapes.small
                    )
                    .clickable { onEventClick(event) }
            )
        }
    }
}

@Composable
fun EventDetailsDialog(
    event: EventEntity,
    onDismiss: () -> Unit
) {
    val detail = buildString {
        // Type with mode
        append("Type: ").append(formatEventType(event)).append('\n')
        
        // Details from payload
        if (event.payload != null) {
            val details = extractDetailsFromPayload(event.payload)
            if (details.isNotEmpty()) {
                append("Details: ").append(details).append('\n')
            }
        }
        
        // Start time
        val startTime = event.start_ts ?: event.ts
        if (startTime != null) {
            append("Start: ").append(formatTime(startTime)).append('\n')
        }
        
        // End time
        if (event.end_ts != null) {
            append("End: ").append(formatTime(event.end_ts)).append('\n')
        }
        
        // Duration
        if (event.start_ts != null && event.end_ts != null) {
            val duration = event.end_ts - event.start_ts
            append("Duration: ").append(formatDuration(duration)).append('\n')
        }
        
        // Note
        if (!event.note.isNullOrBlank()) {
            append("Note: ").append(event.note).append('\n')
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Event Details") },
        text = { Text(detail) }
    )
}

// Helper functions
fun formatDate(date: LocalDate): String {
    val dayOfWeek = date.dayOfWeek.toString().lowercase().replaceFirstChar { it.uppercase() }
    val day = date.dayOfMonth
    val month = date.month.toString().lowercase().replaceFirstChar { it.uppercase() }
    val suffix = when {
        day in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$dayOfWeek, ${day}$suffix $month"
}

fun formatHour(hour: Int): String {
    val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val period = if (hour < 12) "AM" else "PM"
    return "$displayHour:00 $period"
}

fun formatTime(timestamp: Long): String {
    val instant = java.time.Instant.ofEpochSecond(timestamp)
    val localDateTime = java.time.LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    val hour = localDateTime.hour
    val minute = localDateTime.minute
    val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val period = if (hour < 12) "AM" else "PM"
    return "$displayHour:${minute.toString().padStart(2, '0')} $period"
}

fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) {
        "${minutes}m${if (remainingSeconds > 0) " ${remainingSeconds}s" else ""}"
    } else {
        "${remainingSeconds}s"
    }
}

fun formatEventType(event: EventEntity): String {
    val type = event.type.name.replaceFirstChar { it.uppercase() }
    return when (event.type) {
        EventType.feed -> {
            val mode = event.feed_mode?.name?.replaceFirstChar { it.uppercase() } ?: "Unknown"
            "$type ($mode)"
        }
        EventType.nappy -> type
        EventType.sleep -> type
    }
}

fun extractDetailsFromPayload(payload: String): String {
    // Extract details from payload like "L&R*" from "{'details': 'L&R*', 'raw_text': 'o Nursing - L&R* - 9m'}"
    return try {
        val detailsMatch = Regex("'details':\\s*'([^']+)'").find(payload)
        detailsMatch?.groupValues?.get(1) ?: ""
    } catch (e: Exception) {
        ""
    }
}

fun getEventsForHour(events: List<EventEntity>, hour: Int, currentDate: LocalDate): List<EventEntity> {
    val hourStart = getHourStart(hour, currentDate)
    val hourEnd = hourStart + 3600L
    
    return events.filter { event ->
        val eventStart = event.start_ts ?: event.ts ?: 0L
        val eventEnd = event.end_ts ?: eventStart
        
        // Event overlaps with this hour
        eventStart < hourEnd && eventEnd > hourStart
    }
}

fun getHourStart(hour: Int, currentDate: LocalDate): Long {
    val dayStart = currentDate.atTime(7, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
    
    return when {
        hour >= 7 -> dayStart + (hour - 7) * 3600L // 7am-11pm same day
        hour < 7 -> dayStart + (24 - 7 + hour) * 3600L // 12am-6am next day
        else -> dayStart
    }
}
