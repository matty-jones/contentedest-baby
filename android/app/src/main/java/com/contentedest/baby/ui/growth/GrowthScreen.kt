package com.contentedest.baby.ui.growth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.contentedest.baby.data.local.GrowthCategory
import com.contentedest.baby.data.repo.GrowthRepository
import com.patrykandpatrick.vico.compose.cartesian.*
import com.patrykandpatrick.vico.compose.cartesian.axis.*
import com.patrykandpatrick.vico.compose.cartesian.layer.*
import com.patrykandpatrick.vico.compose.m3.*
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.layer.*
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.data.MutableExtraStore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrowthScreen(
    growthRepository: GrowthRepository,
    deviceId: String,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf<GrowthCategory>(GrowthCategory.weight) }
    var showAddDialog by remember { mutableStateOf(false) }
    var growthData by remember { mutableStateOf<List<com.contentedest.baby.data.local.GrowthDataEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Load data on initial mount and when category changes
    LaunchedEffect(selectedCategory) {
        scope.launch {
            growthData = growthRepository.getByCategory(selectedCategory)
        }
    }
    
    // Also reload when screen becomes visible (in case sync happened)
    LaunchedEffect(Unit) {
        scope.launch {
            growthData = growthRepository.getByCategory(selectedCategory)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Show only weight and height categories (exclude head)
                listOf(GrowthCategory.weight, GrowthCategory.height).forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { 
                            Text(
                                text = category.name.replaceFirstChar { it.uppercase() },
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) 
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Add button in place of head category
                FilterChip(
                    selected = false,
                    onClick = { showAddDialog = true },
                    label = { 
                        Text(
                            text = "+",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.fillMaxWidth()
                        ) 
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Chart
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                if (growthData.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No data available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    GrowthChart(
                        data = growthData,
                        category = selectedCategory,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Stats summary
            if (growthData.isNotEmpty()) {
                val latest = growthData.last()
                val previous = growthData.getOrNull(growthData.size - 2) // Next-most-recent value
                val first = growthData.first()
                
                // Calculate age in months from first datapoint to latest
                val ageMonths = calculateAgeMonths(first.ts, latest.ts)
                
                // Calculate percentile (only for weight and height)
                val percentile = if (selectedCategory == GrowthCategory.weight || selectedCategory == GrowthCategory.height) {
                    GrowthPercentileCalculator.calculatePercentile(
                        value = latest.value,
                        unit = latest.unit,
                        ageMonths = ageMonths,
                        category = selectedCategory
                    )
                } else {
                    null
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Latest: ${formatValue(latest.value, latest.unit)}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (previous != null || percentile != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (previous != null) {
                                    // Change is calculated from the next-most-recent value
                                    val change = latest.value - previous.value
                                    Text(
                                        text = "Change: ${if (change >= 0) "+" else ""}${formatValue(change, latest.unit)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (change >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                                if (percentile != null) {
                                    Text(
                                        text = "Percentile: ${String.format("%.1f", percentile)}th",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Date: ${formatDate(latest.ts)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        AddGrowthDialog(
            growthRepository = growthRepository,
            deviceId = deviceId,
            category = selectedCategory,
            onDismiss = { showAddDialog = false },
            onSaved = {
                showAddDialog = false
                scope.launch {
                    growthData = growthRepository.getByCategory(selectedCategory)
                }
            }
        )
    }
}

@Composable
fun GrowthChart(
    data: List<com.contentedest.baby.data.local.GrowthDataEntity>,
    category: GrowthCategory,
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

    // Get unit for formatting
    val unit = remember(data) {
        data.firstOrNull()?.unit ?: ""
    }

    // Calculate age in months for each data point (relative to first data point)
    val firstTs = data.first().ts
    
    // Generate percentile data points (only for weight and height)
    val percentileData = remember(data, category, unit) {
        if (category != GrowthCategory.weight && category != GrowthCategory.height) {
            null
        } else {
            val percentiles = listOf(5.0, 25.0, 50.0, 75.0, 95.0)
            percentiles.map { targetPercentile ->
                data.mapIndexed { index, entity ->
                    val ageMonths = calculateAgeMonths(firstTs, entity.ts)
                    val percentileValue = GrowthPercentileCalculator.calculatePercentileValue(
                        percentile = targetPercentile,
                        ageMonths = ageMonths,
                        category = category,
                        unit = unit
                    )
                    if (percentileValue != null) {
                        LineCartesianLayerModel.Entry(index.toFloat(), percentileValue.toFloat())
                    } else {
                        null
                    }
                }.filterNotNull()
            }
        }
    }

    // Calculate y-axis range: lowest - 10% to highest + 10%
    // CRITICAL: Keep existing axis calculation logic unchanged
    val (yAxisMin, yAxisMax) = remember(data) {
        if (data.isEmpty()) {
            Pair(null, null)
        } else {
            val values = data.map { it.value }
            val minValue = values.minOrNull() ?: 0.0
            val maxValue = values.maxOrNull() ?: 0.0
            val range = maxValue - minValue
            val padding = if (range > 0) range * 0.1 else (maxValue * 0.1).coerceAtLeast(1.0)
            Pair((minValue - padding).toDouble(), (maxValue + padding).toDouble())
        }
    }

    // Get line color for the chart
    val lineColor = remember(category) {
        when (category) {
            GrowthCategory.weight -> Color(0xFF2196F3)
            GrowthCategory.height -> Color(0xFF4CAF50)
            GrowthCategory.head -> Color(0xFFFF9800)
        }
    }
    
    val density = LocalDensity.current

    // Create axes using Vico 2.1.4 API companion object functions with white axis labels
    val startAxis = VerticalAxis.rememberStart(
        label = rememberTextComponent(
            color = Color.White,
            textSize = 12.sp
        ),
        valueFormatter = CartesianValueFormatter { _, value, _ ->
            formatAxisValue(value.toDouble(), unit, category)
        },
        itemPlacer = remember { 
            // Place ticks at integer values (step size of 1.0)
            VerticalAxis.ItemPlacer.step(step = { 1.0 })
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
                val ts = data[index].ts
                val instant = Instant.ofEpochSecond(ts)
                // Use UTC to match server timezone (database is on workstation at 192.168.86.3)
                val date = instant.atZone(ZoneId.of("UTC")).toLocalDate()
                date.format(DateTimeFormatter.ofPattern("MM/dd"))
            } else {
                ""
            }
        },
        itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 2 }) }
    )

    // Create main data line layer with custom Y-axis range (no forced zero) - Vico 2.1.4 API
    // TODO: Add line component styling with reduced opacity for percentile lines
    // Vico 2.1.4 API for setting line colors/opacity per layer needs to be determined
    val mainLineLayer = rememberLineCartesianLayer(
        rangeProvider = CartesianLayerRangeProvider.fixed(
            minY = yAxisMin,
            maxY = yAxisMax
        )
    )

    // Create percentile line layers (only for weight and height)
    // Percentiles: 5th, 25th, 50th, 75th, 95th
    val percentile50Layer = rememberLineCartesianLayer(
        rangeProvider = CartesianLayerRangeProvider.fixed(
            minY = yAxisMin,
            maxY = yAxisMax
        )
    )
    val percentile25Layer = rememberLineCartesianLayer(
        rangeProvider = CartesianLayerRangeProvider.fixed(
            minY = yAxisMin,
            maxY = yAxisMax
        )
    )
    val percentile75Layer = rememberLineCartesianLayer(
        rangeProvider = CartesianLayerRangeProvider.fixed(
            minY = yAxisMin,
            maxY = yAxisMax
        )
    )
    val percentile5Layer = rememberLineCartesianLayer(
        rangeProvider = CartesianLayerRangeProvider.fixed(
            minY = yAxisMin,
            maxY = yAxisMax
        )
    )
    val percentile95Layer = rememberLineCartesianLayer(
        rangeProvider = CartesianLayerRangeProvider.fixed(
            minY = yAxisMin,
            maxY = yAxisMax
        )
    )

    // Combine all layers: main line first, then percentile lines
    val allLayers = remember(percentileData) {
        if (percentileData == null) {
            listOf(mainLineLayer)
        } else {
            // Order: main, 50th, 25th, 75th, 5th, 95th
            listOf(
                mainLineLayer,
                percentile50Layer,
                percentile25Layer,
                percentile75Layer,
                percentile5Layer,
                percentile95Layer
            )
        }
    }

    // Create chart with all layers and axes using Vico 2.1.4 API
    val chart = rememberCartesianChart(
        *allLayers.toTypedArray(),
        startAxis = startAxis,
        bottomAxis = bottomAxis
    )

    // Create chart model with main data and percentile data
    // Percentile data order: [5th, 25th, 50th, 75th, 95th]
    // Layer order: main, 50th, 25th, 75th, 5th, 95th
    val chartModel = remember(data, percentileData) {
        val mainEntries = data.mapIndexed { index, entity ->
            LineCartesianLayerModel.Entry(index.toFloat(), entity.value.toFloat())
        }
        val mainModel = LineCartesianLayerModel(listOf(mainEntries))
        
        if (percentileData == null) {
            CartesianChartModel(
                models = listOf(mainModel)
            )
        } else {
            // PercentileData is [5th, 25th, 50th, 75th, 95th]
            // Layer order: main, 50th, 25th, 75th, 5th, 95th
            val percentile50Model = LineCartesianLayerModel(listOf(percentileData[2])) // 50th
            val percentile25Model = LineCartesianLayerModel(listOf(percentileData[1])) // 25th
            val percentile75Model = LineCartesianLayerModel(listOf(percentileData[3])) // 75th
            val percentile5Model = LineCartesianLayerModel(listOf(percentileData[0]))  // 5th
            val percentile95Model = LineCartesianLayerModel(listOf(percentileData[4]))  // 95th
            
            CartesianChartModel(
                models = listOf(
                    mainModel,
                    percentile50Model,
                    percentile25Model,
                    percentile75Model,
                    percentile5Model,
                    percentile95Model
                )
            )
        }
    }

    val chartScrollState = rememberVicoScrollState()
    
    // State for touch interaction
    var tappedX by remember { mutableStateOf<Float?>(null) }
    var tappedXScreen by remember { mutableStateOf<Float?>(null) }
    var tooltipData by remember { mutableStateOf<TooltipData?>(null) }

    // Use Vico 2.1.4 API with axes now visible
    Box(modifier = modifier) {
        CartesianChartHost(
            chart = chart,
            model = chartModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .pointerInput(data.size, chartScrollState) {
                    detectTapGestures { offset ->
                        // Convert tap offset to chart x-coordinate
                        // Account for padding (16.dp on each side = 32.dp total)
                        val paddingPx = with(density) { 32.dp.toPx() }
                        val visibleWidth = size.width - paddingPx
                        
                        // The chart's full width in data points
                        val maxX = (data.size - 1).toFloat()
                        val totalDataPoints = maxX + 1
                        
                        // Calculate the x position relative to the visible viewport (0 to visibleWidth)
                        val xInViewport = (offset.x - paddingPx / 2).coerceIn(0f, visibleWidth)
                        
                        // Vico charts use data point indices as x-coordinates (0, 1, 2, ...)
                        // The chart scrolls horizontally, so we need to map the tap position
                        // to the correct data point index accounting for scroll.
                        
                        // Get scroll offset from Vico's scroll state
                        // VicoScrollState.value gives the scroll position in pixels
                        val scrollOffsetPx = chartScrollState.value
                        
                        // Vico charts use data point indices (0, 1, 2, ...) as x-coordinates
                        // The chart's pixel width is determined by the number of data points
                        // and Vico's internal spacing (itemPlacer spacing = { 2 })
                        
                        // Calculate the effective width per data point
                        // Vico's horizontal axis spacing = { 2 } means the chart shows approximately
                        // 12-15 data points in the visible viewport (depending on screen size)
                        // We'll calculate this dynamically based on the actual chart dimensions
                        
                        // The key insight: Vico's chart width in pixels scales with the number of data points
                        // Each data point occupies a certain pixel width based on the axis spacing
                        // With spacing = 2, we estimate ~12-14 data points visible per screen
                        val estimatedVisibleDataPoints = 13f // Middle estimate for spacing = 2
                        
                        // Calculate pixel width per data point based on visible viewport
                        val dataPointWidthPx = visibleWidth / estimatedVisibleDataPoints
                        
                        // Calculate total chart width in pixels
                        val totalChartWidthPx = totalDataPoints * dataPointWidthPx
                        
                        // Check if chart is scrollable
                        val isScrollable = totalChartWidthPx > visibleWidth
                        
                        // Convert scroll offset from pixels to data point index
                        // The scroll offset tells us how many pixels we've scrolled from the start
                        val scrollOffsetInDataPoints = if (isScrollable && totalChartWidthPx > 0) {
                            // Convert pixel scroll to data point offset
                            // This tells us which data point is at the left edge of the viewport
                            (scrollOffsetPx / dataPointWidthPx).coerceAtLeast(0f)
                        } else {
                            0f // Not scrollable or at the start
                        }
                        
                        // Calculate which data point index corresponds to the tap position
                        // The tap position is relative to the visible viewport
                        val xRatioInViewport = xInViewport / visibleWidth
                        
                        // Calculate how many data points are visible in the current viewport
                        val visibleDataPoints = if (isScrollable) {
                            estimatedVisibleDataPoints
                        } else {
                            totalDataPoints
                        }
                        
                        // Calculate the data point index within the visible viewport (0 to visibleDataPoints)
                        val dataPointIndexInViewport = xRatioInViewport * visibleDataPoints
                        
                        // Add the scroll offset to get the absolute data point index in the full chart
                        val chartX = scrollOffsetInDataPoints + dataPointIndexInViewport
                        
                        // Clamp to valid range
                        val clampedChartX = chartX.coerceIn(0f, maxX)
                        
                        tappedX = clampedChartX
                        tappedXScreen = offset.x
                        
                        // Calculate interpolated values
                        val interpolatedValue = interpolateValue(clampedChartX, data)
                        val closestIndex = findClosestDayIndex(clampedChartX, data)
                        val closestDay = data[closestIndex]
                        val ageMonths = calculateAgeMonths(firstTs, closestDay.ts)
                        
                        // Calculate interpolated percentile values
                        val percentileValues = if (percentileData != null) {
                            mapOf(
                                5.0 to interpolatePercentileValue(clampedChartX, percentileData[0]),
                                25.0 to interpolatePercentileValue(clampedChartX, percentileData[1]),
                                50.0 to interpolatePercentileValue(clampedChartX, percentileData[2]),
                                75.0 to interpolatePercentileValue(clampedChartX, percentileData[3]),
                                95.0 to interpolatePercentileValue(clampedChartX, percentileData[4])
                            )
                        } else {
                            emptyMap()
                        }
                        
                        // Calculate actual percentile for interpolated measurement
                        val calculatedPercentile = if (interpolatedValue != null && 
                            (category == GrowthCategory.weight || category == GrowthCategory.height)) {
                            GrowthPercentileCalculator.calculatePercentile(
                                value = interpolatedValue,
                                unit = unit,
                                ageMonths = ageMonths,
                                category = category
                            )
                        } else {
                            null
                        }
                        
                        tooltipData = TooltipData(
                            dayIndex = closestIndex,
                            dayTs = closestDay.ts,
                            interpolatedValue = interpolatedValue,
                            interpolatedPercentileValues = percentileValues,
                            calculatedPercentile = calculatedPercentile,
                            unit = unit,
                            interpolatedY = interpolatedValue?.let { 
                                // Convert value to y-coordinate for drawing dot
                                if (yAxisMin != null && yAxisMax != null) {
                                    val yRange = yAxisMax - yAxisMin
                                    if (yRange > 0) {
                                        val yRatio = (it - yAxisMin) / yRange
                                        yRatio
                                    } else null
                                } else null
                            }
                        )
                    }
                },
            scrollState = chartScrollState
        )
        
        // Draw visual indicators (vertical line and dot) using Canvas overlay
        if (tappedXScreen != null && tooltipData != null && tooltipData?.interpolatedY != null) {
            val paddingPx = with(density) { 16.dp.toPx() }
            Canvas(modifier = Modifier.matchParentSize()) {
                val chartHeight = size.height - paddingPx * 2
                
                // Draw red vertical line
                val xPos = tappedXScreen!!
                val yTop = paddingPx
                val yBottom = size.height - paddingPx
                
                drawLine(
                    color = Color.Red,
                    start = Offset(xPos, yTop),
                    end = Offset(xPos, yBottom),
                    strokeWidth = with(density) { 2.dp.toPx() }
                )
                
                // Draw red dot at intersection with main plot line
                val yRatio = tooltipData!!.interpolatedY!!
                val yPos = yBottom - (chartHeight * yRatio.toFloat())
                drawCircle(
                    color = Color.Red,
                    radius = with(density) { 6.dp.toPx() },
                    center = Offset(xPos, yPos)
                )
            }
        }
        
        // Display tooltip
        tooltipData?.let { data ->
            GrowthChartTooltip(
                data = data,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

/**
 * Data class to hold tooltip information
 */
data class TooltipData(
    val dayIndex: Int,
    val dayTs: Long,
    val interpolatedValue: Double?,
    val interpolatedPercentileValues: Map<Double, Double?>,
    val calculatedPercentile: Double?,
    val unit: String,
    val interpolatedY: Double? = null // Y ratio (0-1) for drawing dot
)

/**
 * Tooltip composable for displaying chart interaction data
 */
@Composable
fun GrowthChartTooltip(
    data: TooltipData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Day: ${formatDateShort(data.dayTs)}",
                style = MaterialTheme.typography.titleMedium
            )
            
            if (data.interpolatedValue != null) {
                Text(
                    text = "Value: ${formatValue(data.interpolatedValue, data.unit)}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            if (data.interpolatedPercentileValues.isNotEmpty()) {
                Text(
                    text = "Percentiles:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                data.interpolatedPercentileValues.forEach { (percentile, value) ->
                    if (value != null) {
                        Text(
                            text = "  ${percentile.toInt()}th: ${formatValue(value, data.unit)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            if (data.calculatedPercentile != null) {
                Text(
                    text = "Calculated Percentile: ${String.format("%.1f", data.calculatedPercentile)}th",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

fun formatAxisValue(value: Double, unit: String, category: GrowthCategory): String {
    return when {
        category == GrowthCategory.weight && unit == "lb" -> {
            // Round to nearest integer pound for axis ticks
            val pounds = value.roundToInt()
            "${pounds}lb"
        }
        unit == "in" -> {
            // Round to nearest integer inch for axis ticks
            val inches = value.roundToInt()
            "${inches}in"
        }
        unit == "cm" -> String.format("%.1f cm", value)
        else -> String.format("%.2f", value)
    }
}

fun formatValue(value: Double, unit: String): String {
    return when (unit) {
        "lb" -> {
            val pounds = value.toInt()
            val ounces = ((value - pounds) * 16).toInt()
            if (ounces > 0) {
                "${pounds}lb ${ounces}oz"
            } else {
                "${pounds}lb"
            }
        }
        "in" -> String.format("%.1f in", value)
        "cm" -> String.format("%.1f cm", value)
        else -> String.format("%.2f $unit", value)
    }
}

fun formatDate(ts: Long): String {
    val instant = Instant.ofEpochSecond(ts)
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}

fun formatDateShort(ts: Long): String {
    val instant = Instant.ofEpochSecond(ts)
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("MM/dd"))
}

fun formatDateVeryShort(ts: Long): String {
    val instant = Instant.ofEpochSecond(ts)
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    // Use just day number for compact display
    return date.dayOfMonth.toString()
}

/**
 * Calculate age in months between two timestamps
 * @param firstTs First timestamp (epoch seconds)
 * @param latestTs Latest timestamp (epoch seconds)
 * @return Age in months as a Double
 */
fun calculateAgeMonths(firstTs: Long, latestTs: Long): Double {
    val secondsDiff = latestTs - firstTs
    val daysDiff = secondsDiff / 86400.0 // Convert seconds to days
    val monthsDiff = daysDiff / 30.4375 // Average days per month (365.25 / 12)
    return monthsDiff
}

/**
 * Linearly interpolate a value at a given x position from a list of data points
 * @param x X position (day index, can be fractional)
 * @param data List of growth data entities
 * @return Interpolated value or null if x is out of range
 */
fun interpolateValue(x: Float, data: List<com.contentedest.baby.data.local.GrowthDataEntity>): Double? {
    if (data.isEmpty()) return null
    
    val xInt = x.toInt()
    val xFrac = x - xInt
    
    // Clamp to valid range
    if (xInt < 0) return data.first().value.toDouble()
    if (xInt >= data.size - 1) return data.last().value.toDouble()
    
    // Linear interpolation between two points
    val y1 = data[xInt].value
    val y2 = data[xInt + 1].value
    return y1 + (y2 - y1) * xFrac
}

/**
 * Find the closest day index to a given x position
 * @param x X position (day index)
 * @param data List of growth data entities
 * @return Closest day index
 */
fun findClosestDayIndex(x: Float, data: List<com.contentedest.baby.data.local.GrowthDataEntity>): Int {
    if (data.isEmpty()) return 0
    val xInt = x.toInt()
    return xInt.coerceIn(0, data.size - 1)
}

/**
 * Interpolate percentile value at a given x position
 * @param x X position (day index, can be fractional)
 * @param percentileData List of percentile entries (LineCartesianLayerModel.Entry)
 * @return Interpolated percentile value or null
 */
fun interpolatePercentileValue(x: Float, percentileData: List<LineCartesianLayerModel.Entry>): Double? {
    if (percentileData.isEmpty()) return null
    
    val xInt = x.toInt()
    val xFrac = x - xInt
    
    // Clamp to valid range
    if (xInt < 0) return percentileData.first().y.toDouble()
    if (xInt >= percentileData.size - 1) return percentileData.last().y.toDouble()
    
    // Linear interpolation between two points
    val y1 = percentileData[xInt].y
    val y2 = percentileData[xInt + 1].y
    return (y1 + (y2 - y1) * xFrac).toDouble()
}
