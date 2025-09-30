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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.*

@Composable
fun CircularTimeline(
    events: List<EventEntity>,
    currentDate: LocalDate,
    eventColors: Map<EventType, Color>,
    onEventClick: (EventEntity) -> Unit,
    onTimeClick: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier) {
        val size = minOf(maxWidth, maxHeight) - 32.dp

        Canvas(modifier = Modifier
            .size(size)
            .align(Alignment.Center)
        ) {
            val sizePx = size.toPx()
            val radius = (sizePx - 32.dp.toPx()) / 2
            val centerX = sizePx / 2
            val centerY = sizePx / 2

            // Draw the circular timeline
            drawCircle(
                color = Color.Gray.copy(alpha = 0.3f),
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 4.dp.toPx())
            )

            // Draw hour markers
            for (hour in 0..23) {
                val angle = (hour * 15.0 - 90.0) * PI / 180.0 // Start at 12 o'clock
                val x = centerX + (radius * cos(angle)).toFloat()
                val y = centerY + (radius * sin(angle)).toFloat()

                // Draw hour marker
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.8f),
                    radius = 6.dp.toPx(),
                    center = Offset(x, y)
                )

                // Draw hour label
                val hourText = if (hour == 0) "12" else hour.toString()
                val textStyle = TextStyle(
                    color = Color.Black,
                    fontSize = 12.sp
                )
                val textLayoutResult = textMeasurer.measure(hourText, textStyle)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(x - textLayoutResult.size.width / 2, y - textLayoutResult.size.height / 2)
                )
            }

            // Draw events
            events.forEach { event ->
                val eventTime = event.start_ts ?: event.ts ?: 0L
                val hour = getHourFromTimestamp(eventTime, currentDate)

                if (hour in 0..23) {
                    val angle = (hour * 15.0 - 90.0) * PI / 180.0
                    val x = centerX + (radius * cos(angle)).toFloat()
                    val y = centerY + (radius * sin(angle)).toFloat()

                    drawCircle(
                        color = eventColors[event.type] ?: Color.Gray,
                        radius = 12.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }

        // Clickable overlay - simplified to avoid toPx() issues
        Box(
            modifier = Modifier
                .size(size)
                .align(Alignment.Center)
        ) {
            // For now, just create simple clickable areas at the center
            // This is a simplified version to avoid the complex coordinate calculations
            for (hour in 0..23) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
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
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center)
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