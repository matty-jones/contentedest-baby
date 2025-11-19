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
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import com.patrykandpatrick.vico.compose.cartesian.*
import com.patrykandpatrick.vico.compose.cartesian.axis.*
import com.patrykandpatrick.vico.compose.cartesian.layer.*
import com.patrykandpatrick.vico.compose.m3.*
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.common.Fill
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.layer.*
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import androidx.compose.ui.graphics.toArgb

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
    var selectedTabIndex by remember { mutableStateOf(0) } // 0: List, 1: Graph
    var selectedStatsRange by remember { mutableStateOf(EventStatsRange.WEEK) }
    
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
    
    // Calculate date range: load more days for graph view (60 days to allow scrolling)
    // For list view, use the selected stats range
    val today = LocalDate.now()
    val daysToLoad = if (selectedTabIndex == 1) {
        60 // Graph view: always load 60 days for scrolling
    } else {
        // List view: use selected stats range
        when (selectedStatsRange) {
            EventStatsRange.WEEK -> 7
            EventStatsRange.FORTNIGHT -> 14
            EventStatsRange.MONTH -> 30
        }
    }
    
    // Helper function to reload events
    fun reloadEvents() {
        scope.launch {
            val allEvents = mutableListOf<EventEntity>()
            val currentDaysToLoad = if (selectedTabIndex == 1) {
                60 // Graph view: always load 60 days
            } else {
                // List view: use selected stats range
                when (selectedStatsRange) {
                    EventStatsRange.WEEK -> 7
                    EventStatsRange.FORTNIGHT -> 14
                    EventStatsRange.MONTH -> 30
                }
            }
            val currentToday = LocalDate.now()
            
            // Query events for each day
            for (dayOffset in 0 until currentDaysToLoad) {
                val date = currentToday.minusDays(dayOffset.toLong())
                val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                
                val dayEvents = eventRepository.eventsForDay(dayStart, dayEnd)
                allEvents.addAll(dayEvents.filter { it.type == eventType })
            }
            
            events = allEvents
        }
    }
    
    // Load events - reload when event type, tab, or stats range changes
    LaunchedEffect(eventType, selectedTabIndex, selectedStatsRange) {
        reloadEvents()
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
                        @Suppress("DEPRECATION")
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab row
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("List") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Graph") }
                )
            }
            
            // Tab content
            Box(modifier = Modifier.weight(1f)) {
                    when (selectedTabIndex) {
                    0 -> {
                        // List view
                        if (eventsByDate.isEmpty()) {
                            val rangeText = when (selectedStatsRange) {
                                EventStatsRange.WEEK -> "last 7 days"
                                EventStatsRange.FORTNIGHT -> "last 14 days"
                                EventStatsRange.MONTH -> "last 30 days"
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No ${eventType.name} events in the $rangeText",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
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
                    }
                    1 -> {
                        // Graph view
                        EventGraphView(
                            events = events,
                            eventType = eventType,
                            eventColor = eventColors[eventType] ?: Color.Gray,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            
            // Statistics bar at bottom
            EventStatsBar(
                events = events,
                eventType = eventType,
                selectedRange = selectedStatsRange,
                onRangeChanged = { selectedStatsRange = it },
                modifier = Modifier.fillMaxWidth()
            )
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
                    reloadEvents()
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
                    reloadEvents()
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
                    reloadEvents()
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

enum class EventStatsRange {
    WEEK,
    FORTNIGHT,
    MONTH
}

// Graph view for events
@Composable
fun EventGraphView(
    events: List<EventEntity>,
    eventType: EventType,
    eventColor: Color,
    modifier: Modifier = Modifier
) {
    if (events.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No ${eventType.name} events to display",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    // Group events by date and calculate daily values
    val dailyData = remember(events) {
        val today = LocalDate.now()
        val dataMap = mutableMapOf<LocalDate, DailyEventData>()
        
        // Initialize map with last 60 days
        for (i in 0 until 60) {
            val date = today.minusDays(i.toLong())
            dataMap[date] = DailyEventData(date, 0, 0.0)
        }
        
        // Process events
        events.forEach { event ->
            val timestamp = event.start_ts ?: event.ts ?: return@forEach
            val date = Instant.ofEpochSecond(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            
            val existing = dataMap[date] ?: DailyEventData(date, 0, 0.0)
            
            when (eventType) {
                EventType.sleep -> {
                    val duration = if (event.start_ts != null && event.end_ts != null) {
                        (event.end_ts - event.start_ts) / 3600.0 // Convert to hours
                    } else {
                        0.0
                    }
                    dataMap[date] = existing.copy(
                        count = existing.count + 1,
                        value = existing.value + duration
                    )
                }
                EventType.feed -> {
                    val duration = if (event.start_ts != null && event.end_ts != null) {
                        (event.end_ts - event.start_ts) / 60.0 // Convert to minutes
                    } else {
                        0.0
                    }
                    dataMap[date] = existing.copy(
                        count = existing.count + 1,
                        value = existing.value + duration
                    )
                }
                EventType.nappy -> {
                    dataMap[date] = existing.copy(
                        count = existing.count + 1,
                        value = existing.value + 1.0
                    )
                }
            }
        }
        
        // Sort by date ascending (oldest first) for chart display
        dataMap.values.sortedBy { it.date }.toList()
    }

    // Use Vico chart similar to GrowthScreen
    EventChart(
        data = dailyData,
        eventType = eventType,
        eventColor = eventColor,
        modifier = modifier
    )
}

@Composable
fun EventChart(
    data: List<DailyEventData>,
    eventType: EventType,
    eventColor: Color,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data available",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Calculate y-axis range - always start at zero
    val (yAxisMin, yAxisMax, tickStep) = remember(data, eventType) {
        val values = data.map { it.value }
        val maxValue = values.maxOrNull() ?: 0.0
        val range = maxValue
        val padding = if (range > 0) range * 0.1 else (maxValue * 0.1).coerceAtLeast(1.0)
        val minY = 0.0 // Always start at zero
        val maxY = (maxValue + padding)
        
        // Calculate nice tick step based on event type
        val step = when (eventType) {
            EventType.feed -> {
                // For feed (minutes), round to nice values: 15, 30, 60, 120, etc.
                val rawStep = (maxY - minY) / 5.0 // Aim for ~5 ticks
                when {
                    rawStep <= 15 -> 15.0
                    rawStep <= 30 -> 30.0
                    rawStep <= 60 -> 60.0
                    rawStep <= 120 -> 120.0
                    rawStep <= 180 -> 180.0
                    else -> 240.0
                }
            }
            EventType.sleep -> {
                // For sleep (hours), round to nice values: 0.5, 1, 2, etc.
                val rawStep = (maxY - minY) / 5.0
                when {
                    rawStep <= 0.5 -> 0.5
                    rawStep <= 1.0 -> 1.0
                    rawStep <= 2.0 -> 2.0
                    else -> 3.0
                }
            }
            EventType.nappy -> {
                // For nappy (count), round to nice values: 1, 2, 5, etc.
                val rawStep = (maxY - minY) / 5.0
                when {
                    rawStep <= 1 -> 1.0
                    rawStep <= 2 -> 2.0
                    rawStep <= 5 -> 5.0
                    else -> 10.0
                }
            }
        }
        
        Triple(minY, maxY, step)
    }

    // Create axes
    val startAxis = VerticalAxis.rememberStart(
        label = rememberTextComponent(
            color = Color.White,
            textSize = 12.sp
        ),
        valueFormatter = CartesianValueFormatter { _, value, _ ->
            formatEventAxisValue(value.toDouble(), eventType)
        },
        itemPlacer = remember(tickStep) { 
            VerticalAxis.ItemPlacer.step(step = { tickStep })
        }
    )

    val bottomAxis = HorizontalAxis.rememberBottom(
        label = rememberTextComponent(
            color = Color.White,
            textSize = 12.sp
        ),
        valueFormatter = CartesianValueFormatter { _, x, _ ->
            val index = x.toInt()
            if (index >= 0 && index < data.size) {
                val date = data[index].date
                date.format(DateTimeFormatter.ofPattern("MM/dd"))
            } else {
                ""
            }
        },
        itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 2 }) }
    )

    // Create line style
    val mainLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(eventColor.toArgb())),
        stroke = LineCartesianLayer.LineStroke.Continuous(
            thicknessDp = 3f
        )
    )

    // Create line layer
    val mainLineLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(listOf(mainLine)),
        rangeProvider = CartesianLayerRangeProvider.fixed(
            minY = yAxisMin,
            maxY = yAxisMax
        )
    )

    // Create chart
    val chart = rememberCartesianChart(
        mainLineLayer,
        startAxis = startAxis,
        bottomAxis = bottomAxis
    )

    // Create chart model
    val chartModel = remember(data) {
        val entries = data.mapIndexed { index, dailyData ->
            LineCartesianLayerModel.Entry(index.toFloat(), dailyData.value.toFloat())
        }
        val model = LineCartesianLayerModel(listOf(entries))
        CartesianChartModel(models = listOf(model))
    }

    val chartScrollState = rememberVicoScrollState()

    // Use Vico chart
    Box(modifier = modifier) {
        CartesianChartHost(
            chart = chart,
            model = chartModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            scrollState = chartScrollState
        )
    }
}

fun formatEventAxisValue(value: Double, eventType: EventType): String {
    return when (eventType) {
        EventType.sleep -> {
            // Hours
            String.format("%.1f h", value)
        }
        EventType.feed -> {
            // Minutes - format as "Xh Ym" or just "Ym" if less than an hour
            val totalMinutes = value.roundToInt()
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                hours > 0 -> "${hours}h"
                else -> "${minutes}m"
            }
        }
        EventType.nappy -> {
            // Count
            value.roundToInt().toString()
        }
    }
}

data class DailyEventData(
    val date: LocalDate,
    val count: Int,
    val value: Double // Duration in hours/minutes or count
)

// Statistics bar component
@Composable
fun EventStatsBar(
    events: List<EventEntity>,
    eventType: EventType,
    selectedRange: EventStatsRange,
    onRangeChanged: (EventStatsRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val rangeDays = when (selectedRange) {
        EventStatsRange.WEEK -> 7
        EventStatsRange.FORTNIGHT -> 14
        EventStatsRange.MONTH -> 30
    }
    
    val rangeStart = today.minusDays(rangeDays.toLong())
    val rangeStartEpoch = rangeStart.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
    val rangeEndEpoch = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
    
    val rangeEvents = remember(events, rangeStartEpoch, rangeEndEpoch) {
        events.filter { event ->
            val timestamp = event.start_ts ?: event.ts ?: 0L
            timestamp >= rangeStartEpoch && timestamp < rangeEndEpoch
        }
    }
    
    val stats = remember(rangeEvents, eventType, rangeDays) {
        calculateEventStats(rangeEvents, eventType, rangeDays)
    }
    
    Card(
        modifier = modifier.padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Date range selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedRange == EventStatsRange.WEEK,
                    onClick = { onRangeChanged(EventStatsRange.WEEK) },
                    label = { Text("Week") }
                )
                FilterChip(
                    selected = selectedRange == EventStatsRange.FORTNIGHT,
                    onClick = { onRangeChanged(EventStatsRange.FORTNIGHT) },
                    label = { Text("Fortnight") }
                )
                FilterChip(
                    selected = selectedRange == EventStatsRange.MONTH,
                    onClick = { onRangeChanged(EventStatsRange.MONTH) },
                    label = { Text("Month") }
                )
            }
            
            // Statistics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Mean", stats.mean)
                StatItem("Median", stats.median)
                StatItem("Frequency", stats.frequency)
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

data class EventStats(
    val mean: String,
    val median: String,
    val frequency: String
)

fun calculateEventStats(events: List<EventEntity>, eventType: EventType, rangeDays: Int): EventStats {
    if (events.isEmpty()) {
        return EventStats("--", "--", "0")
    }
    
    return when (eventType) {
        EventType.sleep -> {
            val durations = events
                .filter { it.start_ts != null && it.end_ts != null }
                .map { (it.end_ts!! - it.start_ts!!) / 3600.0 } // Hours
            
            if (durations.isEmpty()) {
                EventStats("--", "--", "0")
            } else {
                val mean = durations.average()
                val sorted = durations.sorted()
                val median = if (sorted.size % 2 == 0) {
                    (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                } else {
                    sorted[sorted.size / 2]
                }
                val frequency = (durations.size.toDouble() / rangeDays)
                
                EventStats(
                    mean = String.format("%.1f h", mean),
                    median = String.format("%.1f h", median),
                    frequency = String.format("%.1f/day", frequency)
                )
            }
        }
        EventType.feed -> {
            val durations = events
                .filter { it.start_ts != null && it.end_ts != null }
                .map { (it.end_ts!! - it.start_ts!!) / 60.0 } // Minutes
            
            if (durations.isEmpty()) {
                val count = events.size
                val frequency = (count.toDouble() / rangeDays)
                EventStats("--", "--", String.format("%.1f/day", frequency))
            } else {
                val mean = durations.average()
                val sorted = durations.sorted()
                val median = if (sorted.size % 2 == 0) {
                    (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                } else {
                    sorted[sorted.size / 2]
                }
                val frequency = (durations.size.toDouble() / rangeDays)
                
                EventStats(
                    mean = String.format("%.0f m", mean),
                    median = String.format("%.0f m", median),
                    frequency = String.format("%.1f/day", frequency)
                )
            }
        }
        EventType.nappy -> {
            // Calculate daily counts
            val today = LocalDate.now()
            val dailyCounts = mutableMapOf<LocalDate, Int>()
            
            // Initialize all days in range with 0
            for (i in 0 until rangeDays) {
                val date = today.minusDays(i.toLong())
                dailyCounts[date] = 0
            }
            
            // Count events per day
            events.forEach { event ->
                val timestamp = event.start_ts ?: event.ts ?: return@forEach
                val date = Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                dailyCounts[date] = (dailyCounts[date] ?: 0) + 1
            }
            
            val counts = dailyCounts.values.filter { it > 0 } // Only days with events
            
            if (counts.isEmpty()) {
                EventStats(
                    mean = "0",
                    median = "0",
                    frequency = String.format("%.1f/day", 0.0)
                )
            } else {
                val mean = counts.average()
                val sorted = counts.sorted()
                val median = if (sorted.size % 2 == 0) {
                    (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                } else {
                    sorted[sorted.size / 2].toDouble()
                }
                val frequency = (events.size.toDouble() / rangeDays)
                
                EventStats(
                    mean = String.format("%.1f", mean),
                    median = String.format("%.1f", median),
                    frequency = String.format("%.1f/day", frequency)
                )
            }
        }
    }
}

