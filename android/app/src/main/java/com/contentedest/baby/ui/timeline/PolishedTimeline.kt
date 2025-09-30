package com.contentedest.baby.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun PolishedTimeline(
    events: List<EventEntity>,
    currentDate: LocalDate,
    eventColors: Map<EventType, Color>,
    onEventClick: (EventEntity) -> Unit,
    onTimeClick: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    
    BoxWithConstraints(modifier = modifier) {
        val width = maxWidth
        val height = maxHeight
        
        // Create a polished horizontal timeline
        Canvas(modifier = Modifier.fillMaxSize()) {
            val timelineHeight = 8.dp.toPx()
            val timelineY = size.height / 2
            val startX = 16.dp.toPx()
            val endX = size.width - 16.dp.toPx()
            val timelineWidth = endX - startX
            
            // Draw the main timeline bar with gradient effect
            drawRect(
                color = Color.Gray.copy(alpha = 0.2f),
                topLeft = Offset(startX, timelineY - timelineHeight / 2),
                size = androidx.compose.ui.geometry.Size(timelineWidth, timelineHeight)
            )
            
            // Draw hour markers
            for (hour in 0..23) {
                val x = startX + (timelineWidth * hour / 23)
                
                // Draw hour marker
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.6f),
                    radius = 4.dp.toPx(),
                    center = Offset(x, timelineY)
                )
                
                // Draw hour label
                val hourText = formatHour(hour)
                val textStyle = TextStyle(
                    color = Color.Black,
                    fontSize = 10.dp.toSp()
                )
                val textLayoutResult = textMeasurer.measure(hourText, textStyle)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(x - textLayoutResult.size.width / 2, timelineY + 16.dp.toPx())
                )
            }
            
            // Draw events as polished segments
            events.forEach { event ->
                val eventTime = event.start_ts ?: event.ts ?: 0L
                val hour = getHourFromTimestamp(eventTime, currentDate)
                
                if (hour in 0..23) {
                    val x = startX + (timelineWidth * hour / 23)
                    
                    // Draw event marker with shadow effect
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.1f),
                        radius = 16.dp.toPx(),
                        center = Offset(x + 2.dp.toPx(), timelineY + 2.dp.toPx())
                    )
                    drawCircle(
                        color = eventColors[event.type] ?: Color.Gray,
                        radius = 14.dp.toPx(),
                        center = Offset(x, timelineY)
                    )
                }
            }
        }
        
        // Clickable overlay
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Create clickable areas for each hour
            for (hour in 0..23) {
                val x = 16.dp + (width - 32.dp) * hour / 23
                
                Box(
                    modifier = Modifier
                        .offset(x = x - 16.dp, y = (height - 32.dp) / 2)
                        .size(32.dp)
                        .clickable {
                            val time = getTimeForHour(hour, currentDate)
                            onTimeClick(time)
                        }
                )
            }
            
            // Create clickable areas for events
            events.forEach { event ->
                val eventTime = event.start_ts ?: event.ts ?: 0L
                val hour = getHourFromTimestamp(eventTime, currentDate)
                
                if (hour in 0..23) {
                    val x = 16.dp + (width - 32.dp) * hour / 23
                    
                    Box(
                        modifier = Modifier
                            .offset(x = x - 16.dp, y = (height - 32.dp) / 2)
                            .size(32.dp)
                            .clickable { onEventClick(event) }
                    )
                }
            }
        }
    }
}

private fun getHourFromTimestamp(timestamp: Long, date: LocalDate): Int {
    val dateTime = java.time.Instant.ofEpochSecond(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    
    return if (dateTime.toLocalDate() == date) {
        dateTime.hour
    } else if (dateTime.toLocalDate() == date.plusDays(1)) {
        dateTime.hour
    } else {
        -1 // Event not on this date
    }
}

// Helper functions (imported from TimelineScreen.kt)
// These functions are already defined in TimelineScreen.kt and will be used from there

