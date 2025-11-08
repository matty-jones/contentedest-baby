package com.contentedest.baby.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun SnakeTimeline(
    events: List<EventEntity>,
    currentDate: LocalDate,
    eventColors: Map<EventType, Color>,
    onEventClick: (EventEntity) -> Unit,
    onTimeClick: (java.time.LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.95f),
    gridColor: Color = Color.White.copy(alpha = 0.10f),
    hoursPerRow: Int = 4,
    rows: Int = 6,
) {
    val density = LocalDensity.current
    
    // Track current time and update every 10 seconds for smooth updates
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalDateTime.now()
            delay(10000) // Update every 10 seconds
        }
    }

    Canvas(
        modifier = modifier.pointerInput(events, currentDate) {
            detectTapGestures { pos ->
                val geom = computeGeometry(size.width.toFloat(), size.height.toFloat(), rows, density)
                val (segments, connectors) = computeEventDrawables(
                    events, currentDate, hoursPerRow, rows, geom
                )
                segments.firstOrNull { it.rect.contains(pos) }?.let {
                    onEventClick(it.event); return@detectTapGestures
                }
                connectors.firstOrNull { it.rect.contains(pos) }?.let {
                    onEventClick(it.event); return@detectTapGestures
                }
                val t = timeFromPosition(pos, currentDate, hoursPerRow, rows, geom)
                onTimeClick(t)
            }
        }
    ) {
        val geom = computeGeometry(size.width.toFloat(), size.height.toFloat(), rows, density)

        // --- Background snake path (fits within canvas) ---
        val path = Path()
        var y = geom.rowCenters[0]
        var goingRight = true
        path.moveTo(geom.innerLeft, y)
        repeat(rows) { r ->
            val endX = if (goingRight) geom.innerRight else geom.innerLeft
            path.lineTo(endX, y)
            if (r != rows - 1) {
                val nextY = geom.rowCenters[r + 1]
                val cpX = if (goingRight) endX + geom.turnRadius else endX - geom.turnRadius
                val cpY = (y + nextY) / 2f
                path.quadraticBezierTo(cpX, cpY, endX, nextY)
                y = nextY
                goingRight = !goingRight
                path.lineTo(if (goingRight) geom.innerLeft else geom.innerRight, y)
            }
        }
        drawPath(
            path = path,
            color = trackColor,
            style = Stroke(width = geom.trackThickness, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Hour ticks
        for (r in 0 until rows) {
            val rowY = geom.rowCenters[r]
            val hourWidth = geom.rowWidth / hoursPerRow
            for (h in 0..hoursPerRow) {
                val x = geom.innerLeft + hourWidth * h
                drawLine(
                    color = gridColor,
                    start = Offset(x, rowY - geom.trackThickness * 0.55f),
                    end = Offset(x, rowY + geom.trackThickness * 0.55f),
                    strokeWidth = 1f
                )
            }
        }

        val (segments, connectors) = computeEventDrawables(events, currentDate, hoursPerRow, rows, geom)

        // Draw current time indicator on the correct row
        val nowEpoch = currentTime.atZone(ZoneId.systemDefault()).toEpochSecond()
        val dayStart = currentDate.atTime(7, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
        val dayEnd = currentDate.plusDays(1).atTime(7, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
        if (nowEpoch >= dayStart && nowEpoch <= dayEnd) {
            val secondsPerRow = hoursPerRow * 3600L
            val totalSeconds = rows * secondsPerRow
            val timeOffset = (nowEpoch - dayStart).coerceIn(0, totalSeconds)
            val rowIndex = floor(timeOffset / secondsPerRow.toDouble()).toInt().coerceIn(0, rows - 1)
            val secondsIntoRow = (timeOffset - rowIndex * secondsPerRow).toFloat()
            val frac = secondsIntoRow / secondsPerRow.toFloat()
            val goingRight = (rowIndex % 2 == 0)
            val currentX = if (goingRight) {
                geom.innerLeft + geom.rowWidth * frac
            } else {
                geom.innerRight - geom.rowWidth * frac
            }
            
            // Draw indicator only on the row where current time exists
            val rowY = geom.rowCenters[rowIndex]
            val lineColor = Color.Red.copy(alpha = 0.8f)
            val lineWidth = with(density) { 3.dp.toPx() }
            val indicatorHeight = geom.trackThickness * 1.2f
            
            // Draw a vertical line segment on the track at the current time position
            drawLine(
                color = lineColor,
                start = Offset(currentX, rowY - indicatorHeight / 2f),
                end = Offset(currentX, rowY + indicatorHeight / 2f),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )
            
            // Draw a small circle at the center of the track for better visibility
            drawCircle(
                color = lineColor,
                radius = with(density) { 4.dp.toPx() },
                center = Offset(currentX, rowY)
            )
        }

        segments.forEach { seg ->
            val color = eventColors[seg.event.type] ?: Color.LightGray
            // Draw only the visible bar height (exclude hit slop) with a small fixed corner radius
            val drawTop = seg.rect.top + geom.hitSlop / 2f
            val drawHeight = geom.barHeight
            val cornerPx = with(density) { 4.dp.toPx() }
            drawRoundRect(
                color = color,
                topLeft = Offset(seg.rect.left, drawTop),
                size = Size(seg.rect.width, drawHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx)
            )
        }

        connectors.forEach { c ->
            val color = eventColors[c.event.type] ?: Color.LightGray
            // Draw quadratic curve to match the snake path
            val p = Path().apply {
                moveTo(c.start.x, c.start.y)
                quadraticBezierTo(c.cp.x, c.cp.y, c.end.x, c.end.y)
            }
            drawPath(
                path = p,
                color = color,
                style = Stroke(width = geom.barHeight, cap = StrokeCap.Butt, join = StrokeJoin.Round)
            )

            // Overlay tangent-based wedges (no overdraw), using correct tangents
            val halfH = geom.barHeight / 2f

            fun computeOffsets(point: Offset, tangent: Offset): Pair<Offset, Offset> {
                val dx = tangent.x
                val dy = tangent.y
                val mag = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
                val nx = -dy / mag
                val ny = dx / mag
                val a = Offset(point.x + nx * halfH, point.y + ny * halfH)
                val b = Offset(point.x - nx * halfH, point.y - ny * halfH)
                // Top has smaller y (screen coords); bottom has larger y
                return if (a.y <= b.y) a to b else b to a
            }

            // Tangent at t=0: 2*(cp - start); Tangent at t=1: 2*(end - cp)
            val tanStart = Offset((c.cp.x - c.start.x) * 2f, (c.cp.y - c.start.y) * 2f)
            val tanEnd = Offset((c.end.x - c.cp.x) * 2f, (c.end.y - c.cp.y) * 2f)
            val (startTop, _) = computeOffsets(c.start, tanStart)
            val (_, endBottom) = computeOffsets(c.end, tanEnd)

            // Start: join right/left event edge to TOP edge of the curve
            val startTopTri = Path().apply {
                moveTo(c.start.x, c.start.y - halfH)
                lineTo(startTop.x, startTop.y)
                lineTo(c.start.x, c.start.y)
                close()
            }
            drawPath(path = startTopTri, color = color)

            // End: join BOTTOM edge of the curve to right/left event edge
            val endBottomTri = Path().apply {
                moveTo(c.end.x, c.end.y + halfH)
                lineTo(endBottom.x, endBottom.y)
                lineTo(c.end.x, c.end.y)
                close()
            }
            drawPath(path = endBottomTri, color = color)
        }

        // Time labels above/below row ends
        val formatter = DateTimeFormatter.ofPattern("h:mm a")
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                // Keep labels small; cap at ~14sp and scale with track thickness
                textSize = with(density) { kotlin.math.min(geom.trackThickness * 0.28f, 14.sp.toPx()) }
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            val labelPadPx = with(density) { 2.dp.toPx() }
            val nudgePx = with(density) { 2.dp.toPx() }
            for (r in 0 until rows) {
                val rowY = geom.rowCenters[r]
                val startHour = 7 + r * hoursPerRow
                val endHour = startHour + hoursPerRow
                val startTime = currentDate.atTime(startHour % 24, 0)
                val endTime = currentDate.atTime(endHour % 24, 0)
                val goingR = (r % 2 == 0)
                val startX = if (goingR) geom.innerLeft else geom.innerRight
                val endX = if (goingR) geom.innerRight else geom.innerLeft
                val topY = rowY - geom.trackThickness * 0.9f
                val bottomY = rowY + geom.trackThickness * 0.9f
                val startText = formatter.format(startTime)
                val endText = formatter.format(endTime)

                // Draw the start label only for the first row; nudge it closer to the track
                if (r == 0) {
                    val sx = startX - paint.measureText(startText) / 2f
                    val sy = topY + nudgePx
                    native.drawText(startText, sx, sy, paint)
                }

                // Draw the end label for each row. Shift horizontally away from the track by at least half the label width,
                // except the special case of the final 7:00 AM which we keep centered and just nudge vertically.
                val endTextWidth = paint.measureText(endText)
                val isFinalSevenAm = (r == rows - 1) && ((endHour % 24) == 7)
                val (ex, ey) = if (isFinalSevenAm) {
                    // Keep centered, nudge toward the track
                    val x = endX - endTextWidth / 2f
                    val y = bottomY - nudgePx
                    x to y
                } else {
                    if (goingR) {
                        // Row ends on the right edge → place label to the left of the track
                        val rightEdge = endX - (endTextWidth * 0.5f + labelPadPx)
                        val x = rightEdge - endTextWidth
                        val y = bottomY
                        x to y
                    } else {
                        // Row ends on the left edge → place label to the right of the track
                        val x = endX + (endTextWidth * 0.5f + labelPadPx)
                        val y = bottomY
                        x to y
                    }
                }
                native.drawText(endText, ex, ey, paint)
            }
        }
    }
}

// --- Geometry ---
private data class Geometry(
    val innerLeft: Float,
    val innerRight: Float,
    val rowCenters: List<Float>,
    val trackThickness: Float,
    val turnRadius: Float,
    val barHeight: Float,
    val rowWidth: Float,
    val hitSlop: Float
)

private fun computeGeometry(widthPx: Float, heightPx: Float, rows: Int, density: Density): Geometry {
    val horizontalPadding = with(density) { 16.dp.toPx() }
    val verticalPadding = with(density) { 28.dp.toPx() }
    val gapRatio = 0.75f
    val usableH = (heightPx - 2f * verticalPadding).coerceAtLeast(1f)
    val trackThickness = usableH / (rows + (rows - 1) * gapRatio)
    val rowGap = gapRatio * trackThickness

    val innerLeft = horizontalPadding + trackThickness / 2f
    val innerRight = widthPx - horizontalPadding - trackThickness / 2f
    val rowWidth = innerRight - innerLeft

    val rowCenters = buildList {
        var y = verticalPadding + trackThickness / 2f
        repeat(rows) {
            add(y); y += trackThickness + rowGap
        }
    }
    val barHeight = trackThickness * 0.74f
    val hitSlop = trackThickness * 0.18f
    val turnRadius = (trackThickness + rowGap) / 2f
    return Geometry(innerLeft, innerRight, rowCenters, trackThickness, turnRadius, barHeight, rowWidth, hitSlop)
}

// --- Event geometry ---
private data class EventSegment(val rect: Rect, val event: EventEntity)
private data class EventConnector(
    val start: Offset, val cp: Offset, val end: Offset,
    val rect: Rect, val event: EventEntity
)

private fun computeEventDrawables(
    events: List<EventEntity>,
    currentDate: LocalDate,
    hoursPerRow: Int,
    rows: Int,
    geom: Geometry
): Pair<List<EventSegment>, List<EventConnector>> {
    val segments = mutableListOf<EventSegment>()
    val connectors = mutableListOf<EventConnector>()

    val dayStart = currentDate.atTime(7, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
    val dayEnd = currentDate.plusDays(1).atTime(7, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
    val secondsPerRow = hoursPerRow * 3600L
    val totalSeconds = rows * secondsPerRow

    fun clamp(ts: Long) = ts.coerceIn(dayStart, dayEnd)

    for (e in events) {
        val rawStart = e.start_ts ?: e.ts ?: continue
        val rawEnd = e.end_ts ?: rawStart + 900
        val s = clamp(rawStart)
        val t = clamp(rawEnd)
        if (t <= s) continue

        val startOff = (s - dayStart).coerceIn(0, totalSeconds)
        val endOff = (t - dayStart).coerceIn(0, totalSeconds)

        val firstRow = floor(startOff / secondsPerRow.toDouble()).toInt()
        val lastRow = floor((endOff - 1) / secondsPerRow.toDouble()).toInt()

        for (r in max(0, firstRow)..min(rows - 1, lastRow)) {
            val rowStartSec = r * secondsPerRow
            val rowEndSec = rowStartSec + secondsPerRow
            val segStart = max(startOff, rowStartSec.toLong())
            val segEnd = min(endOff, rowEndSec.toLong())
            if (segEnd <= segStart) continue

            val goingRight = (r % 2 == 0)
            val startFrac = (segStart - rowStartSec).toFloat() / secondsPerRow.toFloat()
            val endFrac = (segEnd - rowStartSec).toFloat() / secondsPerRow.toFloat()
            val x0 = if (goingRight) geom.innerLeft + geom.rowWidth * startFrac else geom.innerRight - geom.rowWidth * startFrac
            val x1 = if (goingRight) geom.innerLeft + geom.rowWidth * endFrac   else geom.innerRight - geom.rowWidth * endFrac

            val rowY = geom.rowCenters[r]
            val rectTop = rowY - (geom.barHeight + geom.hitSlop) / 2f
            val left = min(x0, x1)
            val right = max(x0, x1)
            segments.add(EventSegment(Rect(left, rectTop, right, rectTop + geom.barHeight + geom.hitSlop), e))

            // map row-start/end → physical left/right depending on direction
            val touchesRowStart = startFrac == 0f
            val touchesRowEnd = endFrac == 1f
            val touchesPhysicalLeft  = if (goingRight) touchesRowStart else touchesRowEnd
            val touchesPhysicalRight = if (goingRight) touchesRowEnd   else touchesRowStart
            val continues = segEnd < endOff

            if (continues && (touchesPhysicalLeft || touchesPhysicalRight) && r < rows - 1) {
                val edgeX = if (touchesPhysicalRight) geom.innerRight else geom.innerLeft
                val nextY = geom.rowCenters[r + 1]
                val cpX = if (touchesPhysicalRight) edgeX + geom.turnRadius else edgeX - geom.turnRadius // kept, but no longer used for drawing
                val cpY = (rowY + nextY) / 2f
                val start = Offset(edgeX, rowY)
                val end = Offset(edgeX, nextY)

                val rect = Rect(
                    (edgeX - geom.barHeight),
                    min(rowY, nextY) - geom.barHeight,
                    (edgeX + geom.barHeight),
                    max(rowY, nextY) + geom.barHeight
                )
                connectors.add(EventConnector(start, Offset(cpX, cpY), end, rect, e))
            }

        }
    }
    return segments to connectors
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
    val rowIndex = geom.rowCenters.indices.minByOrNull { kotlin.math.abs(geom.rowCenters[it] - pos.y) } ?: 0
    val goingRight = (rowIndex % 2 == 0)
    val x = pos.x.coerceIn(geom.innerLeft, geom.innerRight)
    val frac = if (goingRight) (x - geom.innerLeft) / geom.rowWidth else (geom.innerRight - x) / geom.rowWidth
    val secIntoRow = (frac * secondsPerRow).toLong().coerceIn(0, secondsPerRow - 1)
    val epoch = dayStart + rowIndex * secondsPerRow + secIntoRow
    return java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(epoch), ZoneId.systemDefault())
}
