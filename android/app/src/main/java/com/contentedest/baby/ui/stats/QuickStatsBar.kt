package com.contentedest.baby.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import com.contentedest.baby.data.repo.EventRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun QuickStatsBar(
    eventRepository: EventRepository,
    currentDate: LocalDate,
    onEventTypeClick: ((EventType) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var lastSleep by remember { mutableStateOf<EventEntity?>(null) }
    var lastFeed by remember { mutableStateOf<EventEntity?>(null) }
    var lastNappy by remember { mutableStateOf<EventEntity?>(null) }
    var dayEvents by remember { mutableStateOf<List<EventEntity>>(emptyList()) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Event colors matching timeline
    val eventColors = mapOf(
        EventType.sleep to Color(0xFF87CEEB), // Sky blue
        EventType.feed to Color(0xFFFFB6C1), // Light pink
        EventType.nappy to Color(0xFF98FB98) // Pale green
    )

    // Load last events and the selected day's events when date changes
    LaunchedEffect(currentDate) {
        scope.launch {
            lastSleep = eventRepository.getLastSleepEvent()
            lastFeed = eventRepository.getLastFeedEvent()
            lastNappy = eventRepository.getLastNappyEvent()
            // Recalculate day boundaries for the new date
            val newDayStart = currentDate.atTime(7, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
            val newDayEnd = currentDate.plusDays(1).atTime(7, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
            dayEvents = eventRepository.eventsForDay(newDayStart, newDayEnd)
        }
    }

    // Refresh data from database periodically (every 30 seconds)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000)
            scope.launch {
                lastSleep = eventRepository.getLastSleepEvent()
                lastFeed = eventRepository.getLastFeedEvent()
                lastNappy = eventRepository.getLastNappyEvent()
                // Use current date at refresh time
                val refreshDayStart = currentDate.atTime(7, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
                val refreshDayEnd = currentDate.plusDays(1).atTime(7, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
                dayEvents = eventRepository.eventsForDay(refreshDayStart, refreshDayEnd)
            }
        }
    }

    // Update time display every second for smooth updates
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            refreshTrigger++
        }
    }

    // Calculate the selected day's totals
    val daySleepTotal = remember(dayEvents, refreshTrigger) {
        dayEvents
            .filter { it.type == EventType.sleep && it.start_ts != null && it.end_ts != null }
            .sumOf { (it.end_ts ?: 0) - (it.start_ts ?: 0) }
    }
    
    val dayFeedTotal = remember(dayEvents, refreshTrigger) {
        dayEvents
            .filter { it.type == EventType.feed && it.start_ts != null && it.end_ts != null }
            .sumOf { (it.end_ts ?: 0) - (it.start_ts ?: 0) }
    }
    
    val dayNappyCount = remember(dayEvents) {
        dayEvents.count { it.type == EventType.nappy }
    }

    // Determine label text: "Today" if currentDate is today, otherwise show date
    val today = LocalDate.now()
    val labelText = if (currentDate == today) {
        "Today"
    } else {
        formatDateShort(currentDate)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Header row: | Sleep | Feed | Diaper
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Empty label column
            Box(modifier = Modifier.weight(0.8f))
            
            // Sleep header
            Text(
                text = "Sleep",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = eventColors[EventType.sleep] ?: Color.Gray,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = onEventTypeClick != null) {
                        onEventTypeClick?.invoke(EventType.sleep)
                    }
            )
            
            // Feed header
            Text(
                text = "Feed",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = eventColors[EventType.feed] ?: Color.Gray,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = onEventTypeClick != null) {
                        onEventTypeClick?.invoke(EventType.feed)
                    }
            )
            
            // Diaper header
            Text(
                text = "Diaper",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = eventColors[EventType.nappy] ?: Color.Gray,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = onEventTypeClick != null) {
                        onEventTypeClick?.invoke(EventType.nappy)
                    }
            )
        }
        
        Spacer(modifier = Modifier.height(2.dp))
        
        // Last row: Last | time ago (details) | time ago (details) | time ago (details)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Last" label
            Text(
                text = "Last",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(0.8f)
            )
            
            // Sleep last
            StatCell(
                event = lastSleep,
                modifier = Modifier.weight(1f),
                refreshTrigger = refreshTrigger
            )
            
            // Feed last
            StatCell(
                event = lastFeed,
                modifier = Modifier.weight(1f),
                refreshTrigger = refreshTrigger
            )
            
            // Diaper last
            StatCell(
                event = lastNappy,
                modifier = Modifier.weight(1f),
                refreshTrigger = refreshTrigger
            )
        }
        
        Spacer(modifier = Modifier.height(2.dp))
        
        // Today row: Today/Date | total | total | count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Label (shows "Today" or date)
            Text(
                text = labelText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(0.8f)
            )
            
            // Sleep total
            Text(
                text = formatDurationShort(daySleepTotal),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            // Feed total
            Text(
                text = formatDurationShort(dayFeedTotal),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            // Diaper count
            Text(
                text = if (dayNappyCount > 0) "$dayNappyCount" else "0",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCell(
    event: EventEntity?,
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0
) {
    if (event != null) {
        // refreshTrigger causes recomposition, which recalculates these values
        val timeAgo = formatTimeAgo(event)
        val detail = formatEventDetail(event)
        
        Text(
            text = "$timeAgo ($detail)",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
        )
    } else {
        Text(
            text = "—",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = modifier
        )
    }
}

private fun formatTimeAgo(event: EventEntity): String {
    val now = Instant.now().epochSecond
    val eventTime = when (event.type) {
        EventType.sleep -> {
            // For sleep, use end_ts if completed, otherwise start_ts (ongoing)
            event.end_ts ?: event.start_ts
        }
        EventType.feed -> event.ts ?: event.start_ts
        EventType.nappy -> event.ts
    } ?: return "—"
    
    val secondsAgo = now - eventTime
    
    return when {
        secondsAgo < 60 -> "${secondsAgo}s ago"
        secondsAgo < 3600 -> "${secondsAgo / 60}m ago"
        secondsAgo < 86400 -> "${secondsAgo / 3600}h ago"
        else -> "${secondsAgo / 86400}d ago"
    }
}

private fun formatEventDetail(event: EventEntity): String {
    return when (event.type) {
        EventType.sleep -> {
            if (event.end_ts != null && event.start_ts != null) {
                // Completed sleep - show total duration
                val duration = event.end_ts - event.start_ts
                formatDurationShort(duration)
            } else if (event.start_ts != null) {
                // Ongoing sleep - show current duration
                val now = Instant.now().epochSecond
                val duration = now - event.start_ts
                formatDurationShort(duration)
            } else {
                "—"
            }
        }
        EventType.feed -> {
            // For feed, show just the duration (not the mode) in the Last row
            if (event.end_ts != null && event.start_ts != null) {
                val durationSeconds = event.end_ts - event.start_ts
                if (durationSeconds > 0) {
                    formatDurationShort(durationSeconds)
                } else {
                    "—"
                }
            } else {
                "—"
            }
        }
        EventType.nappy -> {
            event.nappy_type?.replaceFirstChar { it.uppercase() } ?: "—"
        }
    }
}

private fun formatDurationShort(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    
    return when {
        hours > 0 -> "${hours}h${if (minutes > 0) " ${minutes}m" else ""}"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

private fun formatDateShort(date: LocalDate): String {
    val dayOfWeek = date.dayOfWeek.toString().lowercase().replaceFirstChar { it.uppercase() }
    val day = date.dayOfMonth
    val month = date.month.toString().lowercase().replaceFirstChar { it.uppercase() }
    // Short format: "Mon, 1st Jan" -> "Mon 1"
    return "$dayOfWeek $day"
}

