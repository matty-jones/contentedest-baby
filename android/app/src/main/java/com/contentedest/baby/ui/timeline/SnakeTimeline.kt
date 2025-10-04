package com.contentedest.baby.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.android.InternalPlatformTextApi
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import android.graphics.Paint
import android.graphics.Typeface
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Snake-style daily timeline.
 *
 * - 7am -> next-day 7am mapped onto 6 horizontal rows (4h per row).
 * - The "snake" path is a continuous thick stroke with rounded caps & joins.
 * - Events are rendered as colored rounded bars within each row.
 * - Taps on event bars call [onEventClick]. Taps elsewhere convert coordinates to a wall-clock time and call [onTimeClick].
 */
@Composable
fun SnakeTimeline(
    events: List<EventEntity>,
    currentDate: LocalDate,
    eventColors: Map<EventType, Color>,
    onEventClick: (EventEntity) -> Unit,
    onTimeClick: (java.time.LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
    // Styling knobs
    trackColor: Color = Color.White.copy(alpha = 0.95f),
    gridColor: Color = Color.White.copy(alpha = 0.12f),
    hoursPerRow: Int = 4,
    rows: Int = 6,
) {
    val density = LocalDensity.current
    // Precompute event draw segments for hit-testing; recomputed inside draw & tap.
    Canvas(
        modifier = modifier
            .pointerInput(events, currentDate) {
                detectTapGestures { pos ->
                    // Recompute geometry and hit-test.
                    val geom = computeGeometry(size.width.toFloat(), size.height.toFloat(), rows, density)
                    val segments = computeEventSegments(
                        events = events,
                        currentDate = currentDate,
                        hoursPerRow = hoursPerRow,
                        rows = rows,
                        geom = geom
                    )
                    // Hit test against segments first
                    segments.firstOrNull { it.rect.contains(pos) }?.let {
                        onEventClick(it.event)
                        return@detectTapGestures
                    }
                    // Map tap to time along row
                    val t = timeFromPosition(pos, currentDate, hoursPerRow, rows, geom)
                    onTimeClick(t)
                }
            }
    ) {
        val geom = computeGeometry(size.width.toFloat(), size.height.toFloat(), rows, density)

        // 1) Background snake path
        val path = Path()
        // start at left of row 0
        var y = geom.rowCenters[0]
        var goingRight = true
        path.moveTo(geom.left, y)
        repeat(rows) { r ->
            val startX = if (goingRight) geom.left else geom.right
            val endX = if (goingRight) geom.right else geom.left
            // horizontal run
            path.lineTo(endX, y)
            // connector to next row (quadratic curve) except after last row
            if (r != rows - 1) {
                val nextY = geom.rowCenters[r + 1]
                val cpX = if (goingRight) endX + geom.turnRadius else endX - geom.turnRadius
                val cpY = (y + nextY) / 2f
                path.quadraticBezierTo(cpX, cpY, endX, nextY)
                y = nextY
                goingRight = !goingRight
                // now on the next row; move back to the other horizontal start to continue lineTo
                path.lineTo(if (goingRight) geom.left else geom.right, y)
            }
        }
        drawPath(
            path = path,
            color = trackColor,
            style = Stroke(width = geom.trackThickness, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        
        // ---- Row corner time labels ----
        val formatter = DateTimeFormatter.ofPattern("h:mm a")
        drawIntoCanvas { canvas ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = geom.trackThickness * 0.42f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            // Each row = 4h; day starts 7:00
            for (r in 0 until rows) {
                val rowY = geom.rowCenters[r]
                val startHour = 7 + r * hoursPerRow
                val endHour   = startHour + hoursPerRow
                val startTime = currentDate.atTime(startHour % 24, 0)
                val endTime   = currentDate.atTime(endHour % 24, 0)

                val goingRight = (r % 2 == 0)
                val leftLabel  = if (goingRight) startTime else endTime
                val rightLabel = if (goingRight) endTime   else startTime

                val leftText  = formatter.format(leftLabel)
                val rightText = formatter.format(rightLabel)

                // small offset so text sits just outside the rounded ends
                val xPad = geom.trackThickness * 0.35f
                val yAdj = geom.trackThickness * 0.15f

                canvas.nativeCanvas.drawText(leftText,  geom.left  - xPad - p.measureText(leftText),  rowY + yAdj, p)
                canvas.nativeCanvas.drawText(rightText, geom.right + xPad,                            rowY + yAdj, p)
            }
        }


        // Hour grid ticks (each hour within a row)
        for (r in 0 until rows) {
            val rowY = geom.rowCenters[r]
            val startX = geom.left
            val endX = geom.right
            val rowWidth = endX - startX
            val hourWidth = rowWidth / hoursPerRow
            for (h in 0..hoursPerRow) {
                val x = startX + hourWidth * h
                drawLine(
                    color = gridColor,
                    start = Offset(x, rowY - geom.trackThickness * 0.6f),
                    end = Offset(x, rowY + geom.trackThickness * 0.6f),
                    strokeWidth = 1f
                )
            }
        }

        // 2) Events
        val segments = computeEventSegments(
            events = events,
            currentDate = currentDate,
            hoursPerRow = hoursPerRow,
            rows = rows,
            geom = geom
        )
        segments.forEach { seg ->
            drawRoundRect(
                color = eventColors[seg.event.type] ?: Color.LightGray,
                topLeft = Offset(seg.rect.left, seg.rect.top),
                size = seg.rect.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(geom.trackThickness / 2f, geom.trackThickness / 2f)
            )
        }
    
        // ---- Bridge caps at row ends to hide white gaps when an event spans rows ----
        val byEvent = segments.groupBy { it.event.id ?: it.event.hashCode() }
        byEvent.values.forEach { segs ->
            val sorted = segs.sortedBy { it.rect.top } // row order top->bottom
            for (i in 0 until sorted.lastIndex) {
                val a = sorted[i]
                val b = sorted[i + 1]
                if (a.event != b.event) continue

                // If 'a' touches a row edge and 'b' touches the opposite edge, add a circle cap
                val aRightEdge = kotlin.math.abs(a.rect.right - geom.right) < geom.trackThickness * 0.15f
                val aLeftEdge  = kotlin.math.abs(a.rect.left  - geom.left)  < geom.trackThickness * 0.15f
                val bRightEdge = kotlin.math.abs(b.rect.right - geom.right) < geom.trackThickness * 0.15f
                val bLeftEdge  = kotlin.math.abs(b.rect.left  - geom.left)  < geom.trackThickness * 0.15f

                val color = eventColors[a.event.type] ?: Color.LightGray
                val radius = (a.rect.height / 2f)

                when {
                    aRightEdge && bRightEdge -> {
                        // U-turn on the right side
                        drawCircle(color, radius, Offset(geom.right, geom.rowCenters[(i % rows)]))
                        drawCircle(color, radius, Offset(geom.right, geom.rowCenters[(i % rows) + 1]))
                    }
                    aLeftEdge && bLeftEdge -> {
                        // U-turn on the left side
                        drawCircle(color, radius, Offset(geom.left, geom.rowCenters[(i % rows)]))
                        drawCircle(color, radius, Offset(geom.left, geom.rowCenters[(i % rows) + 1]))
                    }
                }
            }
        }


    }
}

// --- Geometry helpers ---

private data class Geometry(
    val left: Float,
    val right: Float,
    val rowCenters: List<Float>,
    val trackThickness: Float,
    val innerPadding: Float,
    val turnRadius: Float,
)


private fun computeGeometry(width: Float, height: Float, rows: Int, density: Density): Geometry {
    val horizontalPadding = with(density) { 24.dp.toPx() }
    val verticalPadding = with(density) { 28.dp.toPx() }

    // Fit the snake vertically: totalHeight = rows*T + (rows-1)*G + 2*V
    // Use a stable gap/thickness ratio (G = gapRatio * T) and solve for T.
    val gapRatio = 0.75f
    val usable = (height - 2f * verticalPadding).coerceAtLeast(1f)
    val trackThickness = usable / (rows + (rows - 1) * gapRatio)
    val rowGap = gapRatio * trackThickness

    val left = horizontalPadding
    val right = width - horizontalPadding

    val rowCenters = buildList {
        var y = verticalPadding + trackThickness / 2f
        repeat(rows) {
            add(y)
            y += (trackThickness + rowGap)
        }
    }
    val innerPadding = with(density) { 2.dp.toPx() }
    val turnRadius = (trackThickness + rowGap) / 2f
    return Geometry(left, right, rowCenters, trackThickness, innerPadding, turnRadius)
}


// Represents one drawn segment of an event confined to a single row
private data class EventSegment(val rect: Rect, val event: EventEntity)

private fun computeEventSegments(
    events: List<EventEntity>,
    currentDate: LocalDate,
    hoursPerRow: Int,
    rows: Int,
    geom: Geometry
): List<EventSegment> {
    val dayStart = currentDate.atTime(7, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
    val dayEnd   = currentDate.plusDays(1).atTime(7, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
    val secondsPerRow = hoursPerRow * 3600L
    val totalSeconds  = rows * secondsPerRow

    fun clampToDay(ts: Long) = ts.coerceIn(dayStart, dayEnd)

    val rowWidth  = geom.right - geom.left
    val barHeight = geom.trackThickness * 0.74f
    val hitSlop   = geom.trackThickness * 0.18f  // easier tapping

    val result = mutableListOf<EventSegment>()
    for (event in events) {
        val rawStart = event.start_ts ?: event.ts ?: continue
        val rawEnd   = event.end_ts ?: rawStart + 900 // default 15 min
        val start = clampToDay(rawStart)
        val end   = clampToDay(rawEnd)
        if (end <= start) continue

        val startOffset = (start - dayStart).coerceIn(0, totalSeconds)
        val endOffset   = (end   - dayStart).coerceIn(0, totalSeconds)

        val firstRow = floor(startOffset / secondsPerRow.toDouble()).toInt()
        val lastRow  = floor((endOffset - 1) / secondsPerRow.toDouble()).toInt()

        for (r in max(0, firstRow)..min(rows - 1, lastRow)) {
            val rowStartSec = r * secondsPerRow
            val rowEndSec   = rowStartSec + secondsPerRow
            val segStart = max(startOffset, rowStartSec.toLong())
            val segEnd   = min(endOffset,   rowEndSec.toLong())
            if (segEnd <= segStart) continue

            val rowY = geom.rowCenters[r]
            val yTop = rowY - (barHeight + hitSlop) / 2f
            val goingRight = (r % 2 == 0)

            val startFrac = (segStart - rowStartSec).toFloat() / secondsPerRow.toFloat()
            val endFrac   = (segEnd   - rowStartSec).toFloat() / secondsPerRow.toFloat()

            val x0 = if (goingRight) geom.left + rowWidth * startFrac else geom.right - rowWidth * startFrac
            val x1 = if (goingRight) geom.left + rowWidth * endFrac   else geom.right - rowWidth * endFrac

            // Remove inner padding at edges if the event continues into the connector
            val touchesLeftEdge  = startFrac == 0f
            val touchesRightEdge = endFrac   == 1f
            val padStart = if (touchesLeftEdge) 0f else geom.innerPadding
            val padEnd   = if (touchesRightEdge) 0f else geom.innerPadding

            val left  = min(x0, x1) + padStart
            val right = max(x0, x1) - padEnd
            if (right <= left) continue

            result.add(EventSegment(Rect(left, yTop, right, yTop + barHeight + hitSlop), event))
        }
    }
    return result
}


private fun timeFromPosition(
    pos: Offset,
    currentDate: LocalDate,
    hoursPerRow: Int,
    rows: Int,
    geom: Geometry
): java.time.LocalDateTime {
    val dayStart = currentDate.atTime(7, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
    val secondsPerRow = hoursPerRow * 3600L
    // Find nearest row by vertical distance
    val rowIndex = geom.rowCenters.indices.minByOrNull { kotlin.math.abs(geom.rowCenters[it] - pos.y) } ?: 0
    val goingRight = (rowIndex % 2 == 0)
    val x = pos.x.coerceIn(geom.left, geom.right)
    val frac = if (goingRight) {
        (x - geom.left) / (geom.right - geom.left)
    } else {
        (geom.right - x) / (geom.right - geom.left)
    }
    val secIntoRow = (frac * secondsPerRow).toLong().coerceIn(0, secondsPerRow - 1)
    val epoch = dayStart + rowIndex * secondsPerRow + secIntoRow
    return java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(epoch), ZoneId.systemDefault())
}
