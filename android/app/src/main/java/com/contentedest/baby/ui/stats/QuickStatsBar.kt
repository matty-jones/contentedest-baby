package com.contentedest.baby.ui.stats

import androidx.compose.foundation.background
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

@Composable
fun QuickStatsBar(
    eventRepository: EventRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var lastSleep by remember { mutableStateOf<EventEntity?>(null) }
    var lastFeed by remember { mutableStateOf<EventEntity?>(null) }
    var lastNappy by remember { mutableStateOf<EventEntity?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Event colors matching timeline
    val eventColors = mapOf(
        EventType.sleep to Color(0xFF87CEEB), // Sky blue
        EventType.feed to Color(0xFFFFB6C1), // Light pink
        EventType.nappy to Color(0xFF98FB98) // Pale green
    )

    // Load last events
    LaunchedEffect(Unit) {
        scope.launch {
            lastSleep = eventRepository.getLastSleepEvent()
            lastFeed = eventRepository.getLastFeedEvent()
            lastNappy = eventRepository.getLastNappyEvent()
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sleep column
        StatColumn(
            label = "Sleep",
            event = lastSleep,
            color = eventColors[EventType.sleep] ?: Color.Gray,
            modifier = Modifier.weight(1f),
            refreshTrigger = refreshTrigger
        )
        
        // Feed column
        StatColumn(
            label = "Feed",
            event = lastFeed,
            color = eventColors[EventType.feed] ?: Color.Gray,
            modifier = Modifier.weight(1f),
            refreshTrigger = refreshTrigger
        )
        
        // Diaper column
        StatColumn(
            label = "Diaper",
            event = lastNappy,
            color = eventColors[EventType.nappy] ?: Color.Gray,
            modifier = Modifier.weight(1f),
            refreshTrigger = refreshTrigger
        )
    }
}

@Composable
private fun StatColumn(
    label: String,
    event: EventEntity?,
    color: Color,
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        
        if (event != null) {
            // refreshTrigger causes recomposition, which recalculates these values
            val timeAgo = formatTimeAgo(event)
            val detail = formatEventDetail(event)
            
            Text(
                text = timeAgo,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        } else {
            Text(
                text = "—",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
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
            val mode = event.feed_mode?.name?.replaceFirstChar { it.uppercase() }
                ?: event.details?.let { details ->
                    when {
                        details.contains("L&R", ignoreCase = true) -> "Breast"
                        details.contains("Bottle", ignoreCase = true) -> "Bottle"
                        details.contains("Solids", ignoreCase = true) -> "Solids"
                        else -> "Feed"
                    }
                } ?: "Feed"
            
            val duration = if (event.end_ts != null && event.start_ts != null) {
                val durationSeconds = event.end_ts - event.start_ts
                if (durationSeconds > 0) {
                    " (${formatDurationShort(durationSeconds)})"
                } else {
                    ""
                }
            } else {
                ""
            }
            "$mode$duration"
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

