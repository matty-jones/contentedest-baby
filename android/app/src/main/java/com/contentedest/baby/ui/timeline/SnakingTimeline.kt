package com.contentedest.baby.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SnakingTimeline(
    events: List<EventEntity>,
    currentDate: LocalDate,
    eventColors: Map<EventType, Color>,
    onEventClick: (EventEntity) -> Unit,
    onTimeClick: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) {
    val hoursPerRow = 6 // Number of hours per row
    val totalHours = 24 // 7 AM to 6 AM next day
    val rows = (totalHours + hoursPerRow - 1) / hoursPerRow // Ceiling division
    
    BoxWithConstraints(modifier = modifier) {
        val width = maxWidth
        val rowHeight = 60.dp
        val curveRadius = 20.dp
        
        // Draw the continuous snaking timeline
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val barHeight = 8.dp.toPx()
            val curveRadiusPx = curveRadius.toPx()
            
            // Calculate the path for the continuous snaking timeline
            val path = androidx.compose.ui.graphics.Path()
            
            for (rowIndex in 0 until rows) {
                val startHour = 7 + (rowIndex * hoursPerRow)
                val endHour = minOf(startHour + hoursPerRow - 1, 30)
                val isEvenRow = rowIndex % 2 == 0
                
                val y = (rowIndex * rowHeight.toPx()) + (rowHeight.toPx() / 2)
                val rowWidth = canvasWidth - 32.dp.toPx() // Account for padding
                val startX = 16.dp.toPx()
                val endX = canvasWidth - 16.dp.toPx()
                
                if (isEvenRow) {
                    // Left to right
                    if (rowIndex == 0) {
                        // First row - start the path
                        path.moveTo(startX, y)
                        path.lineTo(endX, y)
                    } else {
                        // Connect from previous row with curve
                        val prevY = ((rowIndex - 1) * rowHeight.toPx()) + (rowHeight.toPx() / 2)
                        path.lineTo(endX, prevY) // Go to end of previous row
                        path.quadraticBezierTo(
                            endX + curveRadiusPx, prevY,
                            endX, y - curveRadiusPx
                        )
                        path.quadraticBezierTo(
                            endX, y,
                            endX - curveRadiusPx, y
                        )
                        path.lineTo(startX, y)
                    }
                } else {
                    // Right to left
                    path.lineTo(startX, y) // Go to start of current row
                    path.quadraticBezierTo(
                        startX - curveRadiusPx, y,
                        startX, y + curveRadiusPx
                    )
                    path.quadraticBezierTo(
                        startX, y + curveRadiusPx,
                        startX + curveRadiusPx, y + curveRadiusPx
                    )
                    path.lineTo(endX, y)
                }
            }
            
            // Draw the timeline path
            drawPath(
                path = path,
                color = Color.Gray.copy(alpha = 0.6f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = barHeight,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
            
            // Draw hour markers along the path
            for (rowIndex in 0 until rows) {
                val startHour = 7 + (rowIndex * hoursPerRow)
                val endHour = minOf(startHour + hoursPerRow - 1, 30)
                val isEvenRow = rowIndex % 2 == 0
                
                val y = (rowIndex * rowHeight.toPx()) + (rowHeight.toPx() / 2)
                val rowWidth = canvasWidth - 32.dp.toPx()
                val startX = 16.dp.toPx()
                val endX = canvasWidth - 16.dp.toPx()
                
                for (hour in startHour..endHour) {
                    val hourIndex = hour - startHour
                    val x = if (isEvenRow) {
                        startX + (rowWidth * hourIndex / (endHour - startHour))
                    } else {
                        endX - (rowWidth * hourIndex / (endHour - startHour))
                    }
                    
                    // Draw hour marker
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.8f),
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
        
        // Overlay event segments and clickable areas
        for (rowIndex in 0 until rows) {
            val startHour = 7 + (rowIndex * hoursPerRow)
            val endHour = minOf(startHour + hoursPerRow - 1, 30)
            val isEvenRow = rowIndex % 2 == 0
            
            // Hour labels for this row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (rowIndex * rowHeight.value).dp + 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isEvenRow) {
                    // Left to right: show start hour on left
                    Text(
                        text = formatHour(if (startHour >= 24) startHour - 24 else startHour),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(4.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formatHour(if (endHour >= 24) endHour - 24 else endHour),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(4.dp)
                    )
                } else {
                    // Right to left: show start hour on right
                    Text(
                        text = formatHour(if (endHour >= 24) endHour - 24 else endHour),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(4.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formatHour(if (startHour >= 24) startHour - 24 else startHour),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
            
            // Event segments and clickable areas for this row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .offset(y = (rowIndex * rowHeight.value).dp)
            ) {
                SnakingTimelineRow(
                    startHour = startHour,
                    endHour = endHour,
                    isEvenRow = isEvenRow,
                    events = events,
                    currentDate = currentDate,
                    eventColors = eventColors,
                    onEventClick = onEventClick,
                    onTimeClick = onTimeClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun SnakingTimelineRow(
    startHour: Int,
    endHour: Int,
    isEvenRow: Boolean,
    events: List<EventEntity>,
    currentDate: LocalDate,
    eventColors: Map<EventType, Color>,
    onEventClick: (EventEntity) -> Unit,
    onTimeClick: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val width = maxWidth
        val height = maxHeight
        val hoursInRow = endHour - startHour + 1
        val hourWidth = width / hoursInRow
        
        // Draw the continuous timeline bar
        Canvas(modifier = Modifier.fillMaxSize()) {
            val heightPx = height.toPx()
            val widthPx = width.toPx()
            val barHeight = heightPx * 0.6f
            val barY = (heightPx - barHeight) / 2
            
            // Draw the main timeline bar
            drawRect(
                color = Color.Gray.copy(alpha = 0.3f),
                topLeft = Offset(0f, barY),
                size = Size(widthPx, barHeight)
            )
            
            // Draw hour markers
            for (i in 0..hoursInRow) {
                val x = (widthPx / hoursInRow) * i
                drawLine(
                    color = Color.Gray.copy(alpha = 0.6f),
                    start = Offset(x, barY),
                    end = Offset(x, barY + barHeight),
                    strokeWidth = 2f
                )
            }
        }
        
        // Draw event segments
        for (hour in startHour..endHour) {
            val displayHour = if (hour >= 24) hour - 24 else hour
            val hourEvents = getEventsForHour(events, displayHour, currentDate)
            val hourIndex = if (isEvenRow) hour - startHour else endHour - hour
            val hourX = hourWidth * hourIndex
            
            // Event segments for this hour
            hourEvents.forEach { event ->
                val eventStart = event.start_ts ?: event.ts ?: 0L
                val eventEnd = event.end_ts ?: (eventStart + 900L) // Default 15 minutes
                
                val hourStart = getHourStart(displayHour, currentDate)
                val hourEnd = hourStart + 3600L
                
                val clampedStart = eventStart.coerceAtLeast(hourStart)
                val clampedEnd = eventEnd.coerceAtMost(hourEnd)
                
                if (clampedStart < clampedEnd) {
                    val startFraction = (clampedStart - hourStart).toFloat() / 3600f
                    val endFraction = (clampedEnd - hourStart).toFloat() / 3600f
                    
                    val segmentStartX = hourX + (hourWidth * startFraction)
                    val segmentWidth = hourWidth * (endFraction - startFraction)
                    
                    Box(
                        modifier = Modifier
                            .offset(x = segmentStartX)
                            .width(segmentWidth)
                            .height(height)
                            .background(
                                eventColors[event.type] ?: Color.Gray,
                                MaterialTheme.shapes.small
                            )
                            .clickable { onEventClick(event) }
                    )
                }
            }
            
            // Clickable area for this hour
            Box(
                modifier = Modifier
                    .offset(x = hourX)
                    .width(hourWidth)
                    .height(height)
                    .clickable {
                        val time = getTimeForHour(displayHour, currentDate)
                        onTimeClick(time)
                    }
            )
        }
    }
}

// Helper functions (imported from TimelineScreen.kt)
// These functions are already defined in TimelineScreen.kt and will be used from there
