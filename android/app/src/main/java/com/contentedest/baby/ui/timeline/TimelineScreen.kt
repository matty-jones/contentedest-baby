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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.timer.TimerStateStorage
import com.contentedest.baby.timer.TimerBackgroundService
import com.contentedest.baby.timer.TimerUpdateWorker
import android.content.Intent
import com.contentedest.baby.data.local.EventType
import com.contentedest.baby.data.local.FeedMode
import com.contentedest.baby.data.repo.EventRepository
import com.contentedest.baby.domain.TimeRules
import com.contentedest.baby.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Data classes to preserve live event dialog states
data class LiveSleepDialogState(
    val running: Boolean = false,
    val startEpoch: Long? = null,
    val endEpoch: Long? = null,
    val details: String? = null
)

data class LiveFeedDialogState(
    val activeSide: com.contentedest.baby.data.local.BreastSide? = null,
    val segments: List<Triple<com.contentedest.baby.data.local.BreastSide, Long, Long>> = emptyList(),
    val currentStart: Long? = null
)

data class LiveNappyDialogState(
    val selected: String? = null
)

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
    deviceId: String,
    date: LocalDate,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val events by vm.events.collectAsState()
    var currentDate by remember { mutableStateOf(date) }
    val timerStateStorage = remember { TimerStateStorage(context) }

    // Load events when date changes
    LaunchedEffect(currentDate) {
        vm.load(currentDate)
    }

    // UI State for details dialog
    var selectedEvent by remember { mutableStateOf<EventEntity?>(null) }
    
    // UI State for add event dialog
    var showAddEventDialog by remember { mutableStateOf(false) }
    var selectedTime by remember { mutableStateOf<LocalDateTime?>(null) }
    
    // UI State for live event dialogs with state preservation
    var savedLiveSleepState by remember { mutableStateOf<LiveSleepDialogState?>(null) }
    var savedLiveFeedState by remember { mutableStateOf<LiveFeedDialogState?>(null) }
    var savedLiveNappyState by remember { mutableStateOf<LiveNappyDialogState?>(null) }
    var showLiveCancelConfirmation by remember { mutableStateOf(false) }
    var pendingLiveTypeForCancel by remember { mutableStateOf<EventType?>(null) }

    // Define event colors
    val eventColors = mapOf(
        EventType.sleep to Color(0xFF87CEEB), // Sky blue
        EventType.feed to Color(0xFFFFB6C1), // Light pink
        EventType.nappy to Color(0xFF98FB98) // Pale green
    )

    var showLivePicker by remember { mutableStateOf(false) }
    var activeLiveType by remember { mutableStateOf<EventType?>(null) }
    
    // Check for active timer state on screen load and restore if needed
    LaunchedEffect(Unit) {
        val activeTimer = timerStateStorage.getActiveTimer()
        if (activeTimer != null && activeTimer.running) {
            // Restore the appropriate dialog based on timer type
            when (activeTimer.type) {
                EventType.sleep -> {
                    // Calculate elapsed time
                    val now = java.time.Instant.now().epochSecond
                    val elapsed = now - activeTimer.startEpoch
                    // Restore sleep dialog state
                    savedLiveSleepState = LiveSleepDialogState(
                        running = true,
                        startEpoch = activeTimer.startEpoch,
                        endEpoch = now, // Current time
                        details = activeTimer.details
                    )
                    activeLiveType = EventType.sleep
                }
                EventType.feed -> {
                    // Restore feed dialog state
                    savedLiveFeedState = LiveFeedDialogState(
                        activeSide = activeTimer.activeSide,
                        segments = activeTimer.segments,
                        currentStart = activeTimer.currentStart
                    )
                    activeLiveType = EventType.feed
                }
                else -> { /* Nappy doesn't need timer persistence */ }
            }
        }
    }

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

        // Timeline canvas fills remaining space below the header
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SnakeTimeline(
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

            // Floating "+" button for live add or restore saved dialog
            FloatingActionButton(
                onClick = { 
                    // Check for saved state first - if exists, restore it
                    // This allows recovery after app crash/close
                    val activeTimer = timerStateStorage.getActiveTimer()
                    when {
                        activeTimer != null && activeTimer.type == EventType.sleep -> {
                            // Restore sleep dialog
                            savedLiveSleepState = LiveSleepDialogState(
                                running = activeTimer.running,
                                startEpoch = activeTimer.startEpoch,
                                endEpoch = if (activeTimer.running) java.time.Instant.now().epochSecond else activeTimer.endEpoch,
                                details = activeTimer.details
                            )
                            activeLiveType = EventType.sleep
                        }
                        activeTimer != null && activeTimer.type == EventType.feed -> {
                            // Restore feed dialog
                            savedLiveFeedState = LiveFeedDialogState(
                                activeSide = activeTimer.activeSide,
                                segments = activeTimer.segments,
                                currentStart = activeTimer.currentStart
                            )
                            activeLiveType = EventType.feed
                        }
                        savedLiveSleepState != null -> activeLiveType = EventType.sleep
                        savedLiveFeedState != null -> activeLiveType = EventType.feed
                        savedLiveNappyState != null -> activeLiveType = EventType.nappy
                        else -> showLivePicker = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Text("+")
            }
        }

        // Details dialog with proper formatting
        if (selectedEvent != null) {
            var showEditDialog by remember { mutableStateOf(false) }
            EventDetailsDialog(
                event = selectedEvent!!,
                onDismiss = { selectedEvent = null },
                onEdit = { showEditDialog = true }
            )
            
            if (showEditDialog) {
                EditEventDialog(
                    event = selectedEvent!!,
                    currentDate = currentDate,
                    eventRepository = eventRepository,
                    deviceId = deviceId,
                    onDismiss = { showEditDialog = false },
                    onEventUpdated = {
                        showEditDialog = false
                        selectedEvent = null
                        // Reload events and trigger sync
                        scope.launch {
                            vm.load(currentDate)
                            // Trigger immediate sync to push the updated event to server
                            SyncWorker.triggerImmediateSync(context, deviceId)
                        }
                    }
                )
            }
        }
        
        // Add event dialog
        if (showAddEventDialog && selectedTime != null) {
            AddEventDialog(
                initialTime = selectedTime!!,
                currentDate = currentDate,
                eventRepository = eventRepository,
                deviceId = deviceId,
                onDismiss = { 
                    showAddEventDialog = false
                    selectedTime = null
                },
                onEventCreated = {
                    showAddEventDialog = false
                    selectedTime = null
                    // Reload events and trigger sync
                    scope.launch {
                        vm.load(currentDate)
                        // Trigger immediate sync to push the new event to server
                        SyncWorker.triggerImmediateSync(context, deviceId)
                    }
                }
            )
        }

        // Live event picker bottom sheet
        if (showLivePicker) {
            LiveEventPickerSheet(
                onDismiss = { showLivePicker = false },
                onPick = { type ->
                    showLivePicker = false
                    // Clear any saved state for this type when explicitly starting a new event from picker
                    // This ensures a fresh start when user explicitly chooses a type
                    when (type) {
                        EventType.sleep -> savedLiveSleepState = null
                        EventType.feed -> savedLiveFeedState = null
                        EventType.nappy -> savedLiveNappyState = null
                    }
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
                    scope.launch { vm.load(currentDate) }
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
                    scope.launch { vm.load(currentDate) }
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
                    scope.launch { vm.load(currentDate) }
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
                text = { Text("This will permanently discard the current event. You can start a new event by pressing the '+' button.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLiveCancelConfirmation = false
                            activeLiveType = null
                            // Clear both persistent and in-memory state when discarding
                            timerStateStorage.clearActiveTimer()
                            // Stop background service
                            val serviceIntent = Intent(context, TimerBackgroundService::class.java)
                            context.stopService(serviceIntent)
                            // Cancel WorkManager updates
                            TimerUpdateWorker.cancelUpdate(context)
                            // Clear in-memory saved state based on the pending type
                            when (pendingLiveTypeForCancel) {
                                EventType.sleep -> savedLiveSleepState = null
                                EventType.feed -> savedLiveFeedState = null
                                EventType.nappy -> savedLiveNappyState = null
                                null -> {}
                            }
                            pendingLiveTypeForCancel = null
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
fun LiveEventPickerSheet(
    onDismiss: () -> Unit,
    onPick: (EventType) -> Unit
) {
    // Disable outside taps - dialog can only be closed via Cancel button
    androidx.compose.ui.window.Dialog(onDismissRequest = { /* Do nothing - prevent accidental dismissal */ }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Start live event", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPick(EventType.sleep) }, modifier = Modifier.weight(1f)) {
                        Text("Sleeping", maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                    }
                    Button(onClick = { onPick(EventType.feed) }, modifier = Modifier.weight(1f)) {
                        Text("Feeding", maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                    }
                    Button(onClick = { onPick(EventType.nappy) }, modifier = Modifier.weight(1f)) {
                        Text("Diaper", maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

@Composable
fun LiveSleepDialog(
    eventRepository: EventRepository,
    deviceId: String,
    savedState: LiveSleepDialogState? = null,
    onStateChanged: (LiveSleepDialogState) -> Unit = {},
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val timerStateStorage = remember { TimerStateStorage(context) }
    
    // Check for persisted timer state on dialog open
    LaunchedEffect(Unit) {
        val persistedState = timerStateStorage.getActiveTimer()
        if (persistedState != null && persistedState.type == EventType.sleep) {
            // Restore from persisted state
            if (persistedState.running) {
                // Calculate elapsed time if timer was running
                val now = java.time.Instant.now().epochSecond
                val elapsed = now - persistedState.startEpoch
                // Restore state with updated end time
                // Note: We'll update the local state below
            }
        }
    }
    
    // Initialize state from saved state, persisted state, or defaults
    var running by remember { 
        mutableStateOf(
            savedState?.running 
                ?: timerStateStorage.getActiveTimer()?.takeIf { it.type == EventType.sleep }?.running 
                ?: false
        ) 
    }
    var startEpoch by remember { 
        mutableStateOf<Long?>(
            savedState?.startEpoch 
                ?: timerStateStorage.getActiveTimer()?.takeIf { it.type == EventType.sleep }?.startEpoch
        ) 
    }
    var endEpoch by remember { 
        mutableStateOf<Long?>(
            savedState?.endEpoch 
                ?: timerStateStorage.getActiveTimer()?.takeIf { it.type == EventType.sleep }?.let { state ->
                    if (state.running) {
                        // If timer was running, calculate current end time
                        java.time.Instant.now().epochSecond
                    } else {
                        state.endEpoch
                    }
                }
        ) 
    }
    var details by remember { 
        mutableStateOf<String?>(
            savedState?.details 
                ?: timerStateStorage.getActiveTimer()?.takeIf { it.type == EventType.sleep }?.details
        ) 
    }
    val scope = rememberCoroutineScope()

    // Save state to both in-memory and persistent storage
    LaunchedEffect(running, startEpoch, endEpoch, details) {
        // Save to in-memory state (for UI state preservation)
        onStateChanged(
            LiveSleepDialogState(
                running = running,
                startEpoch = startEpoch,
                endEpoch = endEpoch,
                details = details
            )
        )
        
        // Save to persistent storage (for background persistence)
        if (startEpoch != null && endEpoch != null) {
            timerStateStorage.saveActiveTimer(
                type = EventType.sleep,
                startEpoch = startEpoch!!,
                endEpoch = endEpoch!!,
                running = running,
                details = details
            )
            
            // Start/stop background service and WorkManager
            if (running) {
                // Start background service
                val serviceIntent = Intent(context, TimerBackgroundService::class.java)
                context.startService(serviceIntent)
                // Schedule periodic WorkManager updates
                TimerUpdateWorker.schedulePeriodicUpdate(context)
            } else {
                // Stop background service
                val serviceIntent = Intent(context, TimerBackgroundService::class.java)
                context.stopService(serviceIntent)
                // Cancel WorkManager updates
                TimerUpdateWorker.cancelUpdate(context)
            }
        }
    }

    LaunchedEffect(running) {
        while (running) {
            endEpoch = java.time.Instant.now().epochSecond
            kotlinx.coroutines.delay(1000)
        }
    }

    // Disable outside taps - dialog can only be closed via Cancel or Save buttons
    androidx.compose.ui.window.Dialog(onDismissRequest = { /* Do nothing - prevent accidental dismissal */ }) {
        Card(modifier = Modifier.padding(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Live Sleep", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Crib", "Arms", "Stroller").forEach { opt ->
                        FilterChip(selected = details == opt, onClick = { details = opt }, label = { Text(opt) })
                    }
                }
                val s = startEpoch
                val e = endEpoch
                val duration = if (s != null && e != null) e - s else 0
                Text("Start: ${s?.let { formatTime(it) } ?: "--"}")
                Text("End: ${e?.let { formatTime(it) } ?: "--"}")
                Text("Duration: ${formatDuration(duration.coerceAtLeast(0))}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (!running) {
                            startEpoch = java.time.Instant.now().epochSecond
                            endEpoch = startEpoch
                            running = true
                        } else {
                            running = false
                        }
                    }, modifier = Modifier.weight(1f)) { Text(if (running) "Pause" else "Play") }
                    OutlinedButton(
                        onClick = {
                            // Clear persistent timer state on cancel
                            timerStateStorage.clearActiveTimer()
                            // Stop background service
                            val serviceIntent = Intent(context, TimerBackgroundService::class.java)
                            context.stopService(serviceIntent)
                            // Cancel WorkManager updates
                            TimerUpdateWorker.cancelUpdate(context)
                            onCancel()
                        }, 
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }
                }
                Button(
                    onClick = {
                        val start = startEpoch
                        val end = endEpoch
                        if (start != null && end != null && end >= start) {
                            scope.launch {
                                eventRepository.insertSleepSpan(deviceId, start, end, details)
                                // Clear persistent timer state
                                timerStateStorage.clearActiveTimer()
                                // Stop background service
                                val serviceIntent = Intent(context, TimerBackgroundService::class.java)
                                context.stopService(serviceIntent)
                                // Cancel WorkManager updates
                                TimerUpdateWorker.cancelUpdate(context)
                                onSaved()
                                onDismiss()
                            }
                        }
                    },
                    enabled = startEpoch != null && endEpoch != null && !running,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save") }
            }
        }
    }
}

@Composable
fun LiveFeedDialog(
    eventRepository: EventRepository,
    deviceId: String,
    savedState: LiveFeedDialogState? = null,
    onStateChanged: (LiveFeedDialogState) -> Unit = {},
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val timerStateStorage = remember { TimerStateStorage(context) }
    val scope = rememberCoroutineScope()
    
    // Check for persisted timer state on dialog open
    LaunchedEffect(Unit) {
        val persistedState = timerStateStorage.getActiveTimer()
        if (persistedState != null && persistedState.type == EventType.feed) {
            // State will be restored in remember blocks below
        }
    }
    
    // Initialize state from saved state, persisted state, or defaults
    var activeSide by remember { 
        mutableStateOf<com.contentedest.baby.data.local.BreastSide?>(
            savedState?.activeSide 
                ?: timerStateStorage.getActiveTimer()?.takeIf { it.type == EventType.feed }?.activeSide
        ) 
    }
    var segments by remember { 
        mutableStateOf(
            savedState?.segments 
                ?: timerStateStorage.getActiveTimer()?.takeIf { it.type == EventType.feed }?.segments
                ?: emptyList()
        ) 
    }
    var currentStart by remember { 
        mutableStateOf<Long?>(
            savedState?.currentStart 
                ?: timerStateStorage.getActiveTimer()?.takeIf { it.type == EventType.feed }?.currentStart
        ) 
    }
    
    // Save state whenever it changes
    LaunchedEffect(activeSide, segments, currentStart) {
        // Save to in-memory state (for UI state preservation)
        onStateChanged(
            LiveFeedDialogState(
                activeSide = activeSide,
                segments = segments,
                currentStart = currentStart
            )
        )
        
        // Save to persistent storage (for background persistence)
        // For feed, we need to determine if timer is "running" (activeSide != null)
        val isRunning = activeSide != null
        val startEpoch = segments.firstOrNull()?.second ?: currentStart ?: 0L
        val endEpoch = if (isRunning && currentStart != null) {
            java.time.Instant.now().epochSecond
        } else {
            segments.lastOrNull()?.third ?: currentStart ?: 0L
        }
        
        if (startEpoch > 0) {
            timerStateStorage.saveActiveTimer(
                type = EventType.feed,
                startEpoch = startEpoch,
                endEpoch = endEpoch,
                running = isRunning,
                segments = segments,
                activeSide = activeSide,
                currentStart = currentStart
            )
            
            // Start/stop background service and WorkManager
            if (isRunning) {
                // Start background service
                val serviceIntent = Intent(context, TimerBackgroundService::class.java)
                context.startService(serviceIntent)
                // Schedule periodic WorkManager updates
                TimerUpdateWorker.schedulePeriodicUpdate(context)
            } else {
                // Stop background service
                val serviceIntent = Intent(context, TimerBackgroundService::class.java)
                context.stopService(serviceIntent)
                // Cancel WorkManager updates
                TimerUpdateWorker.cancelUpdate(context)
            }
        }
    }

    var totalSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(activeSide, currentStart, segments) {
        while (activeSide != null) {
            val now = java.time.Instant.now().epochSecond
            val openDur = if (currentStart != null) (now - currentStart!!).coerceAtLeast(0) else 0
            totalSeconds = segments.sumOf { (it.third - it.second).coerceAtLeast(0) } + openDur
            delay(1000)
        }
        // When paused, finalize total without open segment
        totalSeconds = segments.sumOf { (it.third - it.second).coerceAtLeast(0) }
    }

    fun toggle(side: com.contentedest.baby.data.local.BreastSide) {
        val now = java.time.Instant.now().epochSecond
        if (activeSide == side) {
            // Pause current side
            val start = currentStart ?: return
            segments = segments + Triple(side, start, now)
            activeSide = null
            currentStart = null
        } else {
            // Switch sides: close previous if open, then start new
            if (activeSide != null && currentStart != null) {
                segments = segments + Triple(activeSide!!, currentStart!!, now)
            }
            activeSide = side
            currentStart = now
        }
    }

    // totalSeconds is kept live by LaunchedEffect above

    // Disable outside taps - dialog can only be closed via Cancel or Save buttons
    androidx.compose.ui.window.Dialog(onDismissRequest = { /* Do nothing - prevent accidental dismissal */ }) {
        Card(modifier = Modifier.padding(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Live Feeding (Breast)", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { toggle(com.contentedest.baby.data.local.BreastSide.left) }, modifier = Modifier.weight(1f)) {
                        if (activeSide == com.contentedest.baby.data.local.BreastSide.left) {
                            Text("⏸")
                            Spacer(Modifier.width(8.dp))
                            Text("Left")
                        } else {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play Left")
                            Spacer(Modifier.width(8.dp))
                            Text("Left")
                        }
                    }
                    Button(onClick = { toggle(com.contentedest.baby.data.local.BreastSide.right) }, modifier = Modifier.weight(1f)) {
                        if (activeSide == com.contentedest.baby.data.local.BreastSide.right) {
                            Text("⏸")
                            Spacer(Modifier.width(8.dp))
                            Text("Right")
                        } else {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play Right")
                            Spacer(Modifier.width(8.dp))
                            Text("Right")
                        }
                    }
                }
                Text("Total: ${formatDuration(totalSeconds)}")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(segments.size) { idx ->
                        val (side, s, e) = segments[idx]
                        AssistChip(onClick = {}, label = { Text("${side.name.first().uppercase()} ${formatDuration((e - s).coerceAtLeast(0))}") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            // Clear persistent timer state on cancel
                            timerStateStorage.clearActiveTimer()
                            // Stop background service
                            val serviceIntent = Intent(context, TimerBackgroundService::class.java)
                            context.stopService(serviceIntent)
                            // Cancel WorkManager updates
                            TimerUpdateWorker.cancelUpdate(context)
                            onCancel()
                        }, 
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            if (activeSide != null && currentStart != null) {
                                val now = java.time.Instant.now().epochSecond
                                segments = segments + Triple(activeSide!!, currentStart!!, now)
                                activeSide = null
                                currentStart = null
                            }
                            scope.launch {
                                eventRepository.insertBreastFeed(deviceId, segments)
                                // Clear persistent timer state
                                timerStateStorage.clearActiveTimer()
                                // Stop background service
                                val serviceIntent = Intent(context, TimerBackgroundService::class.java)
                                context.stopService(serviceIntent)
                                // Cancel WorkManager updates
                                TimerUpdateWorker.cancelUpdate(context)
                                onSaved(); onDismiss()
                            }
                        },
                        enabled = segments.isNotEmpty() && activeSide == null,
                        modifier = Modifier.weight(1f)
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
fun LiveNappyDialog(
    eventRepository: EventRepository,
    deviceId: String,
    savedState: LiveNappyDialogState? = null,
    onStateChanged: (LiveNappyDialogState) -> Unit = {},
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    // Initialize state from saved state or defaults
    var selected by remember { mutableStateOf<String?>(savedState?.selected) }
    
    // Save state whenever it changes
    LaunchedEffect(selected) {
        onStateChanged(
            LiveNappyDialogState(selected = selected)
        )
    }

    // Disable outside taps - dialog can only be closed via Cancel or Save buttons
    androidx.compose.ui.window.Dialog(onDismissRequest = { /* Do nothing - prevent accidental dismissal */ }) {
        Card(modifier = Modifier.padding(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Quick Diaper", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Wet", "Dirty", "Mixed").forEach { opt ->
                        FilterChip(selected = selected == opt, onClick = { selected = opt }, label = { Text(opt) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            val now = java.time.Instant.now().epochSecond
                            scope.launch {
                                eventRepository.logNappy(now, deviceId, selected ?: "Unknown", null)
                                onSaved(); onDismiss()
                            }
                        },
                        enabled = selected != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("Save") }
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
    onDismiss: () -> Unit,
    onEdit: () -> Unit
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
        dismissButton = {
            TextButton(onClick = onEdit) { Text("Edit") }
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
    deviceId: String,
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
    
    // Update end time when start time changes (maintain current duration)
    LaunchedEffect(startTime) {
        endTime = startTime.plusHours(durationHours.toLong()).plusMinutes(durationMinutes.toLong())
    }
    
    // Update end time when duration changes
    LaunchedEffect(durationHours, durationMinutes) {
        endTime = startTime.plusHours(durationHours.toLong()).plusMinutes(durationMinutes.toLong())
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(7.dp),
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
                                    eventRepository = eventRepository,
                                    deviceId = deviceId
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
fun EditEventDialog(
    event: EventEntity,
    currentDate: LocalDate,
    eventRepository: EventRepository,
    deviceId: String,
    onDismiss: () -> Unit,
    onEventUpdated: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // Initialize state from event
    val eventStartTime = event.start_ts ?: event.ts ?: java.time.Instant.now().epochSecond
    val eventEndTime = event.end_ts ?: (eventStartTime + 900L) // Default 15 minutes if no end time
    
    var selectedEventType by remember { mutableStateOf(event.type) }
    var selectedDetails by remember { mutableStateOf(event.details) }
    var startTime by remember { 
        mutableStateOf(
            java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(eventStartTime),
                ZoneId.systemDefault()
            )
        )
    }
    var endTime by remember { 
        mutableStateOf(
            java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(eventEndTime),
                ZoneId.systemDefault()
            )
        )
    }
    var durationHours by remember { 
        mutableStateOf(
            ((eventEndTime - eventStartTime) / 3600).toInt()
        )
    }
    var durationMinutes by remember { 
        mutableStateOf(
            (((eventEndTime - eventStartTime) % 3600) / 60).toInt()
        )
    }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }
    
    // Update duration when end time changes
    LaunchedEffect(endTime) {
        val duration = java.time.Duration.between(startTime, endTime)
        durationHours = duration.toHours().toInt()
        durationMinutes = (duration.toMinutes() % 60).toInt()
    }
    
    // Update end time when start time changes (maintain current duration)
    LaunchedEffect(startTime) {
        endTime = startTime.plusHours(durationHours.toLong()).plusMinutes(durationMinutes.toLong())
    }
    
    // Update end time when duration changes
    LaunchedEffect(durationHours, durationMinutes) {
        endTime = startTime.plusHours(durationHours.toLong()).plusMinutes(durationMinutes.toLong())
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(7.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Edit Event",
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
                            // Update the event
                            scope.launch {
                                eventRepository.updateEvent(
                                    eventId = event.event_id,
                                    eventType = selectedEventType,
                                    details = selectedDetails,
                                    startTime = startTime,
                                    endTime = endTime,
                                    deviceId = deviceId
                                )
                                onEventUpdated()
                            }
                        },
                        enabled = selectedEventType != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Update",
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
            modifier = Modifier.padding(7.dp),
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
        EventType.feed -> listOf("*L&R", "L", "R", "L&R*", "Bottle", "Solids")
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
    eventRepository: EventRepository,
    deviceId: String
) {
    val startTimestamp = startTime.atZone(ZoneId.systemDefault()).toEpochSecond()
    val endTimestamp = endTime.atZone(ZoneId.systemDefault()).toEpochSecond()
    
    when (eventType) {
        EventType.sleep -> {
            // Use insertSleepSpan to create a sleep event with both start and end times
            eventRepository.insertSleepSpan(deviceId, startTimestamp, endTimestamp, details, null)
        }
        EventType.feed -> {
            val feedMode = when (details) {
                "Bottle" -> FeedMode.bottle
                "Solids" -> FeedMode.solids
                else -> FeedMode.breast
            }
            if (feedMode == FeedMode.breast) {
                // For breast feeds, determine the breast side from details
                val side = when (details) {
                    "L", "L&R*", "*L&R" -> com.contentedest.baby.data.local.BreastSide.left
                    "R" -> com.contentedest.baby.data.local.BreastSide.right
                    else -> com.contentedest.baby.data.local.BreastSide.left // Default to left
                }
                // Create a breast feed event with a single segment
                eventRepository.insertBreastFeed(
                    deviceId,
                    listOf(Triple(side, startTimestamp, endTimestamp)),
                    details,
                    null
                )
            } else {
                // For bottle and solids, create a feed event with start and end times
                eventRepository.insertFeedSpan(deviceId, startTimestamp, endTimestamp, feedMode, details, null)
            }
        }
        EventType.nappy -> {
            eventRepository.createNappy(startTimestamp, deviceId, details ?: "Unknown", null, endTimestamp)
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
