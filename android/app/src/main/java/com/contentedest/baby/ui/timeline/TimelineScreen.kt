package com.contentedest.baby.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import com.contentedest.baby.data.local.FeedMode
import com.contentedest.baby.data.repo.EventRepository
import com.contentedest.baby.domain.TimeRules
import com.contentedest.baby.ui.timeline.PolishedTimeline
import com.contentedest.baby.ui.timeline.SmoothTimeline
import com.contentedest.baby.ui.timeline.SnakingTimeline
import com.contentedest.baby.ui.timeline.CircularTimeline
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
    
    // UI State for add event dialog
    var showAddEventDialog by remember { mutableStateOf(false) }
    var selectedTime by remember { mutableStateOf<LocalDateTime?>(null) }
    
    // Timeline type selector
    var selectedTimelineType by remember { mutableStateOf("Polished") }

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

        // Timeline type selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Polished", "Smooth", "Snaking", "Circular").forEach { timelineType ->
                FilterChip(
                    selected = selectedTimelineType == timelineType,
                    onClick = { selectedTimelineType = timelineType },
                    label = { Text(timelineType) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Timeline content
        val scrollState = rememberScrollState()
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTimelineType) {
                "Polished" -> PolishedTimeline(
                    events = events,
                    currentDate = currentDate,
                    eventColors = eventColors,
                    onEventClick = { selectedEvent = it },
                    onTimeClick = { time ->
                        selectedTime = time
                        showAddEventDialog = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
                "Smooth" -> SmoothTimeline(
                    events = events,
                    currentDate = currentDate,
                    eventColors = eventColors,
                    onEventClick = { selectedEvent = it },
                    onTimeClick = { time ->
                        selectedTime = time
                        showAddEventDialog = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
                "Snaking" -> SnakingTimeline(
                    events = events,
                    currentDate = currentDate,
                    eventColors = eventColors,
                    onEventClick = { selectedEvent = it },
                    onTimeClick = { time ->
                        selectedTime = time
                        showAddEventDialog = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
                "Circular" -> CircularTimeline(
                    events = events,
                    currentDate = currentDate,
                    eventColors = eventColors,
                    onEventClick = { selectedEvent = it },
                    onTimeClick = { time ->
                        selectedTime = time
                        showAddEventDialog = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Details dialog with proper formatting
        if (selectedEvent != null) {
            EventDetailsDialog(
                event = selectedEvent!!,
                onDismiss = { selectedEvent = null }
            )
        }
        
        // Add event dialog
        if (showAddEventDialog && selectedTime != null) {
            AddEventDialog(
                initialTime = selectedTime!!,
                currentDate = currentDate,
                eventRepository = eventRepository,
                onDismiss = { 
                    showAddEventDialog = false
                    selectedTime = null
                },
                onEventCreated = {
                    showAddEventDialog = false
                    selectedTime = null
                    // Reload events
                    scope.launch {
                        vm.load(currentDate)
                    }
                }
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
    onTimeClick: (LocalDateTime) -> Unit,
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
                    modifier = Modifier
                        .width(60.dp)
                        .clickable {
                            val time = getTimeForHour(displayHour, currentDate)
                            onTimeClick(time)
                        }
                        .padding(vertical = 4.dp)
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
                        .clickable {
                            val time = getTimeForHour(displayHour, currentDate)
                            onTimeClick(time)
                        }
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
        append("Type: ").append(formatEventTypeForDetails(event)).append('\n')
        
        // Details
        val details = formatEventDetails(event)
        if (details.isNotEmpty()) {
            append("Details: ").append(details).append('\n')
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
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    
    return when {
        hours > 0 -> {
            val minutesText = if (minutes > 0) " ${minutes}m" else ""
            val secondsText = if (remainingSeconds > 0) " ${remainingSeconds}s" else ""
            "${hours}h$minutesText$secondsText"
        }
        minutes > 0 -> {
            val secondsText = if (remainingSeconds > 0) " ${remainingSeconds}s" else ""
            "${minutes}m$secondsText"
        }
        else -> "${remainingSeconds}s"
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

fun getTimeForHour(hour: Int, currentDate: LocalDate): LocalDateTime {
    return when {
        hour >= 7 -> currentDate.atTime(hour, 0) // 7am-11pm same day
        hour < 7 -> currentDate.plusDays(1).atTime(hour, 0) // 12am-6am next day
        else -> currentDate.atTime(7, 0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    initialTime: LocalDateTime,
    currentDate: LocalDate,
    eventRepository: EventRepository,
    onDismiss: () -> Unit,
    onEventCreated: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedEventType by remember { mutableStateOf<EventType?>(null) }
    var selectedDetails by remember { mutableStateOf<String?>(null) }
    var startTime by remember { mutableStateOf(initialTime) }
    var endTime by remember { mutableStateOf(initialTime.plusMinutes(30)) }
    var durationHours by remember { mutableStateOf(0) }
    var durationMinutes by remember { mutableStateOf(30) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }
    
    // Update duration when end time changes
    LaunchedEffect(endTime) {
        val duration = java.time.Duration.between(startTime, endTime)
        durationHours = duration.toHours().toInt()
        durationMinutes = (duration.toMinutes() % 60).toInt()
    }
    
    // Update end time when duration changes
    LaunchedEffect(durationHours, durationMinutes) {
        endTime = startTime.plusHours(durationHours.toLong()).plusMinutes(durationMinutes.toLong())
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Add Event",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                // Event Type Selection
                Text(
                    text = "Event Type",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EventType.values().forEach { type ->
                        FilterChip(
                            selected = selectedEventType == type,
                            onClick = { 
                                selectedEventType = type
                                selectedDetails = null // Reset details when type changes
                            },
                            label = { Text(type.name.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                
                // Event Details Selection (only show if event type is selected)
                if (selectedEventType != null) {
                    Text(
                        text = "Details",
                        style = MaterialTheme.typography.titleMedium
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(getDetailsForEventType(selectedEventType!!)) { detail ->
                            FilterChip(
                                selected = selectedDetails == detail,
                                onClick = { 
                                    selectedDetails = detail
                                    // Set smart duration defaults for nappy events
                                    if (selectedEventType == EventType.nappy) {
                                        val (hours, minutes) = getNappyDuration(detail)
                                        durationHours = hours
                                        durationMinutes = minutes
                                    }
                                },
                                label = { 
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodySmall
                                    ) 
                                }
                            )
                        }
                    }
                }
                
                // Start Time
                Text(
                    text = "Start Time",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(
                    onClick = { showStartTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(formatTimeForDisplay(startTime))
                }
                
                // End Time and Duration (side by side)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // End Time
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "End Time",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedButton(
                            onClick = { showEndTimePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(formatTimeForDisplay(endTime))
                        }
                    }
                    
                    // Duration
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedButton(
                            onClick = { showDurationPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(formatDurationDisplay(durationHours, durationMinutes))
                        }
                    }
                }
                
                // Action Buttons
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
                            // Create the event
                            scope.launch {
                                createEvent(
                                    eventType = selectedEventType!!,
                                    details = selectedDetails,
                                    startTime = startTime,
                                    endTime = endTime,
                                    eventRepository = eventRepository
                                )
                                onEventCreated()
                            }
                        },
                        enabled = selectedEventType != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Create",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
    
    // Time Pickers
    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = startTime,
            onTimeSelected = { 
                startTime = it
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }
    
    if (showEndTimePicker) {
        TimePickerDialog(
            initialTime = endTime,
            onTimeSelected = { 
                endTime = it
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
    
    if (showDurationPicker) {
        DurationPickerDialog(
            initialHours = durationHours,
            initialMinutes = durationMinutes,
            onDurationSelected = { hours, minutes ->
                durationHours = hours
                durationMinutes = minutes
                showDurationPicker = false
            },
            onDismiss = { showDurationPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialTime: LocalDateTime,
    onTimeSelected: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = remember {
        TimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = false
        )
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Select Time",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                TimePicker(state = timePickerState)
                
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
                            val selectedTime = LocalDateTime.of(
                                initialTime.year,
                                initialTime.month,
                                initialTime.dayOfMonth,
                                timePickerState.hour,
                                timePickerState.minute
                            )
                            onTimeSelected(selectedTime)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@Composable
fun DurationPickerDialog(
    initialHours: Int,
    initialMinutes: Int,
    onDurationSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hours by remember { mutableStateOf(initialHours) }
    var minutes by remember { mutableStateOf(initialMinutes) }
    var hoursText by remember { mutableStateOf(initialHours.toString()) }
    var minutesText by remember { mutableStateOf(initialMinutes.toString()) }
    var hoursFocused by remember { mutableStateOf(false) }
    var minutesFocused by remember { mutableStateOf(false) }
    
    val hoursFocusRequester = remember { FocusRequester() }
    val minutesFocusRequester = remember { FocusRequester() }
    
    // Sync text fields when values change externally
    LaunchedEffect(initialHours, initialMinutes) {
        if (!hoursFocused) {
            hoursText = initialHours.toString()
            hours = initialHours
        }
        if (!minutesFocused) {
            minutesText = initialMinutes.toString()
            minutes = initialMinutes
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Select Duration",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hours")
                        OutlinedTextField(
                            value = hoursText,
                            onValueChange = { 
                                hoursText = it
                                hours = it.toIntOrNull() ?: 0
                            },
                            modifier = Modifier
                                .focusRequester(hoursFocusRequester)
                                .onFocusChanged { focusState ->
                                    hoursFocused = focusState.isFocused
                                    if (focusState.isFocused) {
                                        hoursText = ""
                                    }
                                },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            singleLine = true
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Minutes")
                        OutlinedTextField(
                            value = minutesText,
                            onValueChange = { 
                                minutesText = it
                                minutes = it.toIntOrNull() ?: 0
                            },
                            modifier = Modifier
                                .focusRequester(minutesFocusRequester)
                                .onFocusChanged { focusState ->
                                    minutesFocused = focusState.isFocused
                                    if (focusState.isFocused) {
                                        minutesText = ""
                                    }
                                },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            singleLine = true
                        )
                    }
                }
                
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
                            // Use the current values from text fields
                            val finalHours = hoursText.toIntOrNull() ?: 0
                            val finalMinutes = minutesText.toIntOrNull() ?: 0
                            onDurationSelected(finalHours, finalMinutes)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

// Helper functions
fun getDetailsForEventType(eventType: EventType): List<String> {
    return when (eventType) {
        EventType.sleep -> listOf("Crib", "Arms", "Stroller")
        EventType.feed -> listOf("L&R*", "L", "R", "L&R*", "Bottle", "Solids")
        EventType.nappy -> listOf("Dirty", "Wet", "Mixed")
    }
}

fun formatTimeForDisplay(time: LocalDateTime): String {
    val hour = if (time.hour == 0) 12 else if (time.hour > 12) time.hour - 12 else time.hour
    val period = if (time.hour < 12) "AM" else "PM"
    return "$hour:${time.minute.toString().padStart(2, '0')} $period"
}

fun formatDurationDisplay(hours: Int, minutes: Int): String {
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "0m"
    }
}

fun getNappyDuration(detail: String): Pair<Int, Int> {
    return when (detail) {
        "Dirty" -> Pair(0, 5) // 5 minutes
        "Wet" -> Pair(0, 3)   // 3 minutes
        "Mixed" -> Pair(0, 4) // 4 minutes
        else -> Pair(0, 5)    // Default 5 minutes
    }
}

suspend fun createEvent(
    eventType: EventType,
    details: String?,
    startTime: LocalDateTime,
    endTime: LocalDateTime,
    eventRepository: EventRepository
) {
    val deviceId = "device-${System.currentTimeMillis()}" // TODO: Get actual device ID
    val startTimestamp = startTime.atZone(ZoneId.systemDefault()).toEpochSecond()
    val endTimestamp = endTime.atZone(ZoneId.systemDefault()).toEpochSecond()
    
    when (eventType) {
        EventType.sleep -> {
            eventRepository.createSleep(startTimestamp, deviceId, details)
        }
        EventType.feed -> {
            val feedMode = when (details) {
                "Bottle" -> FeedMode.bottle
                "Solids" -> FeedMode.solids
                else -> FeedMode.breast
            }
            eventRepository.startFeed(startTimestamp, deviceId, feedMode, details)
        }
        EventType.nappy -> {
            eventRepository.createNappy(startTimestamp, deviceId, details ?: "Unknown", null)
        }
    }
}

fun formatEventTypeForDetails(event: EventEntity): String {
    return event.type.name.replaceFirstChar { it.uppercase() }
}

fun formatEventDetails(event: EventEntity): String {
    return when (event.type) {
        EventType.feed -> {
            val mode = event.feed_mode?.name?.replaceFirstChar { it.uppercase() } 
                ?: event.details?.let { details ->
                    when {
                        details.contains("L&R") -> "Breast"
                        details.contains("Bottle") -> "Bottle"
                        details.contains("Solids") -> "Solids"
                        else -> details
                    }
                } ?: "Unknown"
            val details = event.details ?: ""
            if (details.isNotEmpty() && mode != details) {
                "$mode, $details"
            } else {
                mode
            }
        }
        EventType.nappy -> event.nappy_type ?: event.details ?: "Unknown"
        EventType.sleep -> event.details ?: "Unknown"
    }
}
