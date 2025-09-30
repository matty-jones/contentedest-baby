package com.contentedest.baby.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun SmoothTimeline(
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

        // Create a smooth flowing timeline path
        val timelinePath = createSmoothTimelinePath(width, height)

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw the main timeline path
            drawPath(
                path = timelinePath,
                color = Color.Gray.copy(alpha = 0.6f),
                style = Stroke(
                    width = 8.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )

            // Draw hour markers along the path
            drawHourMarkers(timelinePath, width, height)

            // Draw events along the path
            drawEventsAlongPath(
                timelinePath = timelinePath,
                events = events,
                currentDate = currentDate,
                eventColors = eventColors,
                width = width,
                height = height
            )
        }

        // Overlay clickable areas
        TimelineClickableOverlay(
            timelinePath = timelinePath,
            events = events,
            currentDate = currentDate,
            onEventClick = onEventClick,
            onTimeClick = onTimeClick,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun createSmoothTimelinePath(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp): Path {
    val path = Path()
    val widthPx = width.toPx()
    val heightPx = height.toPx()

    // Create a smooth S-curve that flows across the screen
    val startX = 16.dp.toPx()
    val endX = widthPx - 16.dp.toPx()
    val midY = heightPx / 2
    val curveHeight = heightPx * 0.6f

    // Start at top left
    path.moveTo(startX, 16.dp.toPx())

    // Create smooth curves that flow across the screen
    val segments = 6 // Number of curve segments
    val segmentWidth = (endX - startX) / segments

    for (i in 0 until segments) {
        val x1 = startX + (segmentWidth * i)
        val x2 = startX + (segmentWidth * (i + 1))
        val y1 = if (i % 2 == 0) 16.dp.toPx() else heightPx - 16.dp.toPx()
        val y2 = if (i % 2 == 0) heightPx - 16.dp.toPx() else 16.dp.toPx()

        // Create smooth curve between points
        val controlX1 = x1 + segmentWidth * 0.3f
        val controlY1 = y1
        val controlX2 = x2 - segmentWidth * 0.3f
        val controlY2 = y2

        path.cubicTo(controlX1, controlY1, controlX2, controlY2, x2, y2)
    }

    return path
}

private fun DrawScope.drawHourMarkers(
    timelinePath: Path,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp
) {
    val widthPx = width.toPx()
    val heightPx = height.toPx()

    // Draw hour markers at regular intervals along the path
    val totalHours = 24
    val segmentWidth = (widthPx - 32.dp.toPx()) / totalHours

    for (hour in 0 until totalHours) {
        val x = 16.dp.toPx() + (segmentWidth * hour)
        val y = if (hour % 2 == 0) 16.dp.toPx() else heightPx - 16.dp.toPx()

        drawCircle(
            color = Color.Gray.copy(alpha = 0.8f),
            radius = 4.dp.toPx(),
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawEventsAlongPath(
    timelinePath: Path,
    events: List<EventEntity>,
    currentDate: LocalDate,
    eventColors: Map<EventType, Color>,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp
) {
    val widthPx = width.toPx()
    val heightPx = height.toPx()
    val segmentWidth = (widthPx - 32.dp.toPx()) / 24

    events.forEach { event ->
        val eventTime = event.start_ts ?: event.ts ?: 0L
        val hour = getHourFromTimestamp(eventTime, currentDate)

        if (hour in 0..23) {
            val x = 16.dp.toPx() + (segmentWidth * hour)
            val y = if (hour % 2 == 0) 16.dp.toPx() else heightPx - 16.dp.toPx()

            drawCircle(
                color = eventColors[event.type] ?: Color.Gray,
                radius = 8.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun TimelineClickableOverlay(
    timelinePath: Path,
    events: List<EventEntity>,
    currentDate: LocalDate,
    onEventClick: (EventEntity) -> Unit,
    onTimeClick: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val width = maxWidth
        val height = maxHeight
        val segmentWidth = (width - 32.dp) / 24

        // Create clickable areas for each hour
        for (hour in 0..23) {
            val x = 16.dp + (segmentWidth * hour)
            val y = if (hour % 2 == 0) 16.dp else height - 16.dp

            Box(
                modifier = Modifier
                    .offset(x = x, y = y)
                    .size(segmentWidth, 32.dp)
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
                val x = 16.dp + (segmentWidth * hour)
                val y = if (hour % 2 == 0) 16.dp else height - 16.dp

                Box(
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .size(16.dp)
                        .clickable { onEventClick(event) }
                )
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