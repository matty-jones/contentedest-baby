package com.contentedest.baby.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import com.contentedest.baby.data.repo.EventRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    eventType: EventType,
    eventRepository: EventRepository,
    deviceId: String,
    onNavigateBack: () -> Unit,
    onEventClick: (EventEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<EventEntity>>(emptyList()) }
    
    // UI State for live event dialogs with state preservation
    var savedLiveSleepState by remember { mutableStateOf<LiveSleepDialogState?>(null) }
    var savedLiveFeedState by remember { mutableStateOf<LiveFeedDialogState?>(null) }
    var savedLiveNappyState by remember { mutableStateOf<LiveNappyDialogState?>(null) }
    var showLiveCancelConfirmation by remember { mutableStateOf(false) }
    var pendingLiveTypeForCancel by remember { mutableStateOf<EventType?>(null) }
    
    var showLivePicker by remember { mutableStateOf(false) }
    var activeLiveType by remember { mutableStateOf<EventType?>(null) }
    
    // Event colors matching timeline
    val eventColors = mapOf(
        EventType.sleep to Color(0xFF87CEEB), // Sky blue
        EventType.feed to Color(0xFFFFB6C1), // Light pink
        EventType.nappy to Color(0xFF98FB98) // Pale green
    )
    
    // Calculate date range: last 7 days (including today)
    val today = LocalDate.now()
    val sevenDaysAgo = today.minusDays(6) // 7 days including today
    
    // Load events for the last 7 days
    LaunchedEffect(eventType) {
        scope.launch {
            val allEvents = mutableListOf<EventEntity>()
            
            // Query events for each day from 7 days ago to today
            for (dayOffset in 0..6) {
                val date = today.minusDays(dayOffset.toLong())
                val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                
                val dayEvents = eventRepository.eventsForDay(dayStart, dayEnd)
                allEvents.addAll(dayEvents.filter { it.type == eventType })
            }
            
            events = allEvents
        }
    }
    
    // Group events by date
    val eventsByDate = remember(events) {
        events.groupBy { event ->
            val timestamp = event.start_ts ?: event.ts ?: 0L
            if (timestamp > 0) {
                Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            } else {
                LocalDate.now()
            }
        }
        .toList()
        .sortedByDescending { it.first } // Sort dates descending (most recent first)
        .map { (date, eventList) ->
            date to eventList.sortedByDescending { event ->
                event.start_ts ?: event.ts ?: 0L
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "${eventType.name.replaceFirstChar { it.uppercase() }} Events",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    // If there's saved state, restore the appropriate dialog
                    when {
                        savedLiveSleepState != null -> activeLiveType = EventType.sleep
                        savedLiveFeedState != null -> activeLiveType = EventType.feed
                        savedLiveNappyState != null -> activeLiveType = EventType.nappy
                        else -> showLivePicker = true
                    }
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("+")
            }
        }
    ) { innerPadding ->
        if (eventsByDate.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No ${eventType.name} events in the last 7 days",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                eventsByDate.forEach { (date, dateEvents) ->
                    item {
                        // Date header
                        Text(
                            text = formatDate(date),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(dateEvents) { event ->
                        EventListItem(
                            event = event,
                            eventColor = eventColors[eventType] ?: Color.Gray,
                            onClick = { onEventClick(event) }
                        )
                    }
                }
            }
        }
        
        // Live event picker bottom sheet
        if (showLivePicker) {
            LiveEventPickerSheet(
                onDismiss = { showLivePicker = false },
                onPick = { type ->
                    showLivePicker = false
                    activeLiveType = type
                }
            )
        }

        when (activeLiveType) {
            EventType.sleep -> LiveSleepDialog(
                eventRepository = eventRepository,
                deviceId = deviceId,
                savedState = savedLiveSleepState,
                onStateChanged = { state -> savedLiveSleepState = state },
                onDismiss = { activeLiveType = null },
                onCancel = {
                    pendingLiveTypeForCancel = EventType.sleep
                    showLiveCancelConfirmation = true
                },
                onSaved = { 
                    scope.launch {
                        // Reload events
                        val today = LocalDate.now()
                        val allEvents = mutableListOf<EventEntity>()
                        for (dayOffset in 0..6) {
                            val date = today.minusDays(dayOffset.toLong())
                            val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                            val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                            val dayEvents = eventRepository.eventsForDay(dayStart, dayEnd)
                            allEvents.addAll(dayEvents.filter { it.type == eventType })
                        }
                        events = allEvents
                    }
                    activeLiveType = null
                    savedLiveSleepState = null // Clear saved state after successful save
                }
            )
            EventType.feed -> LiveFeedDialog(
                eventRepository = eventRepository,
                deviceId = deviceId,
                savedState = savedLiveFeedState,
                onStateChanged = { state -> savedLiveFeedState = state },
                onDismiss = { activeLiveType = null },
                onCancel = {
                    pendingLiveTypeForCancel = EventType.feed
                    showLiveCancelConfirmation = true
                },
                onSaved = { 
                    scope.launch {
                        // Reload events
                        val today = LocalDate.now()
                        val allEvents = mutableListOf<EventEntity>()
                        for (dayOffset in 0..6) {
                            val date = today.minusDays(dayOffset.toLong())
                            val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                            val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                            val dayEvents = eventRepository.eventsForDay(dayStart, dayEnd)
                            allEvents.addAll(dayEvents.filter { it.type == eventType })
                        }
                        events = allEvents
                    }
                    activeLiveType = null
                    savedLiveFeedState = null // Clear saved state after successful save
                }
            )
            EventType.nappy -> LiveNappyDialog(
                eventRepository = eventRepository,
                deviceId = deviceId,
                savedState = savedLiveNappyState,
                onStateChanged = { state -> savedLiveNappyState = state },
                onDismiss = { activeLiveType = null },
                onCancel = {
                    pendingLiveTypeForCancel = EventType.nappy
                    showLiveCancelConfirmation = true
                },
                onSaved = { 
                    scope.launch {
                        // Reload events
                        val today = LocalDate.now()
                        val allEvents = mutableListOf<EventEntity>()
                        for (dayOffset in 0..6) {
                            val date = today.minusDays(dayOffset.toLong())
                            val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                            val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                            val dayEvents = eventRepository.eventsForDay(dayStart, dayEnd)
                            allEvents.addAll(dayEvents.filter { it.type == eventType })
                        }
                        events = allEvents
                    }
                    activeLiveType = null
                    savedLiveNappyState = null // Clear saved state after successful save
                }
            )
            null -> {}
        }
        
        // Cancel confirmation dialog for live events
        if (showLiveCancelConfirmation) {
            AlertDialog(
                onDismissRequest = { showLiveCancelConfirmation = false },
                title = { Text("Discard changes?") },
                text = { Text("Your event information will be saved so you can continue later by pressing the '+' button.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLiveCancelConfirmation = false
                            activeLiveType = null
                            // Keep saved state so user can restore it later with '+' button
                        }
                    ) {
                        Text("Discard")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showLiveCancelConfirmation = false }
                    ) {
                        Text("Continue Editing")
                    }
                }
            )
        }
    }
}

@Composable
fun EventListItem(
    event: EventEntity,
    eventColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Details and time info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Details (if available)
                val detailsText = formatEventDetailsForList(event)
                if (detailsText.isNotEmpty()) {
                    Text(
                        text = detailsText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = eventColor
                    )
                }
                
                // Time range on one line
                val startTime = event.start_ts ?: event.ts
                if (startTime != null) {
                    if (event.end_ts != null) {
                        // Show time range: "Start - End"
                        Text(
                            text = "${formatTime(startTime)} - ${formatTime(event.end_ts)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    } else {
                        // Show single time
                        Text(
                            text = formatTime(startTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Right side: Duration (if available)
            if (event.start_ts != null && event.end_ts != null) {
                val duration = event.end_ts - event.start_ts
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatEventDetailsForList(event: EventEntity): String {
    return when (event.type) {
        EventType.sleep -> {
            event.details ?: "Sleep"
        }
        EventType.feed -> {
            val mode = event.feed_mode?.name?.replaceFirstChar { it.uppercase() } 
                ?: event.details?.let { details ->
                    when {
                        details.contains("L&R") -> "Breast"
                        details.contains("Bottle") -> "Bottle"
                        details.contains("Solids") -> "Solids"
                        else -> details
                    }
                } ?: "Feed"
            val details = event.details ?: ""
            if (details.isNotEmpty() && mode != details) {
                "$mode, $details"
            } else {
                mode
            }
        }
        EventType.nappy -> {
            event.nappy_type?.replaceFirstChar { it.uppercase() } 
                ?: event.details?.replaceFirstChar { it.uppercase() }
                ?: "Diaper"
        }
    }
}

