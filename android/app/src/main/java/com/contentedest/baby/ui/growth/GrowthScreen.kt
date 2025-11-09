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

    // Calculate y-axis range: lowest - 10% to highest + 10%
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

    // Create line layer with custom Y-axis range (no forced zero) - Vico 2.1.4 API
    val lineLayer = rememberLineCartesianLayer(
        rangeProvider = CartesianLayerRangeProvider.fixed(
            minY = yAxisMin,
            maxY = yAxisMax
        )
    )

    // Create chart with line layer and axes using Vico 2.1.4 API
    val chart = rememberCartesianChart(
        lineLayer,
        startAxis = startAxis,
        bottomAxis = bottomAxis
    )

    // Create chart model with data
    val chartModel = remember(data) {
        val entries = data.mapIndexed { index, entity ->
            LineCartesianLayerModel.Entry(index.toFloat(), entity.value.toFloat())
        }
        CartesianChartModel(
            models = listOf(LineCartesianLayerModel(listOf(entries)))
        )
    }

    val chartScrollState = rememberVicoScrollState()

    // Use Vico 2.1.4 API with axes now visible
    CartesianChartHost(
        chart = chart,
        model = chartModel,
        modifier = modifier.padding(16.dp),
        scrollState = chartScrollState
    )
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
