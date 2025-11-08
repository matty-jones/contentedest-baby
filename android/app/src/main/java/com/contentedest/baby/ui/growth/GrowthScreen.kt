package com.contentedest.baby.ui.growth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.contentedest.baby.data.local.GrowthCategory
import com.contentedest.baby.data.repo.GrowthRepository
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollState
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.core.entry.entryModelOf
import kotlinx.coroutines.launch
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
                GrowthCategory.values().forEach { category ->
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
                        if (percentile != null) {
                            Text(
                                text = "Percentile: ${String.format("%.1f", percentile)}th",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (previous != null) {
                            // Change is calculated from the next-most-recent value
                            val change = latest.value - previous.value
                            Text(
                                text = "Change: ${if (change >= 0) "+" else ""}${formatValue(change, latest.unit)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (change >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
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

        // Floating Action Button
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
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

    // Create line chart - we'll try to set axisValuesOverrider directly on the LineChart object
    val chart = lineChart()
    
    // Try to set rangeProvider on layers using reflection (side effect)
    LaunchedEffect(yAxisMin, yAxisMax, chart) {
        if (yAxisMin != null && yAxisMax != null) {
            try {
                android.util.Log.d("GrowthChart", "Attempting to set rangeProvider: min=$yAxisMin, max=$yAxisMax")
                
                // Access the lines field (which contains the layers)
                val linesField = chart.javaClass.getDeclaredField("lines")
                linesField.isAccessible = true
                val lines = linesField.get(chart)
                android.util.Log.d("GrowthChart", "Lines type: ${lines?.javaClass?.name}")
                
                // Try to create a rangeProvider - check what class exists
                val rangeProviderClassNames = listOf(
                    "com.patrykandpatrick.vico.core.model.CartesianLayerRangeProvider",
                    "com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider"
                )
                
                var rangeProviderClass: Class<*>? = null
                for (className in rangeProviderClassNames) {
                    try {
                        rangeProviderClass = Class.forName(className)
                        android.util.Log.d("GrowthChart", "Found rangeProvider class: $className")
                        break
                    } catch (e: ClassNotFoundException) {
                        // Try next
                    }
                }
                
                if (rangeProviderClass != null) {
                    val overrider = java.lang.reflect.Proxy.newProxyInstance(
                        rangeProviderClass.classLoader,
                        arrayOf(rangeProviderClass)
                    ) { _, method, _ ->
                        when (method.name) {
                            "getMinY" -> yAxisMin
                            "getMaxY" -> yAxisMax
                            else -> null
                        }
                    }
                    
                    // Try to set on the chart itself first
                    try {
                        val axisValuesOverriderField = chart.javaClass.getDeclaredField("axisValuesOverrider")
                        axisValuesOverriderField.isAccessible = true
                        axisValuesOverriderField.set(chart, overrider)
                        android.util.Log.d("GrowthChart", "Successfully set axisValuesOverrider on chart")
                    } catch (e: NoSuchFieldException) {
                        android.util.Log.d("GrowthChart", "axisValuesOverrider field not found on chart, trying lines")
                        // Try to set on lines (if it's a list)
                        if (lines is List<*>) {
                            lines.forEach { line ->
                                try {
                                    android.util.Log.d("GrowthChart", "Line type: ${line!!.javaClass.name}")
                                    val lineFields = line.javaClass.declaredFields
                                    android.util.Log.d("GrowthChart", "Line fields: ${lineFields.map { it.name }.joinToString()}")
                                    
                                    // Try different field names
                                    val fieldNames = listOf("rangeProvider", "axisValuesOverrider", "yAxisRangeProvider")
                                    var lineSuccess = false
                                    for (fieldName in fieldNames) {
                                        try {
                                            val field = line.javaClass.getDeclaredField(fieldName)
                                            field.isAccessible = true
                                            field.set(line, overrider)
                                            android.util.Log.d("GrowthChart", "Successfully set $fieldName on line")
                                            lineSuccess = true
                                            break
                                        } catch (e: NoSuchFieldException) {
                                            // Try next
                                        }
                                    }
                                    if (!lineSuccess) {
                                        android.util.Log.d("GrowthChart", "Could not find rangeProvider field on line")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.d("GrowthChart", "Failed to set on line: ${e.message}")
                                }
                            }
                        }
                    }
                } else {
                    android.util.Log.w("GrowthChart", "Could not find rangeProvider class")
                }
            } catch (e: Exception) {
                android.util.Log.w("GrowthChart", "Failed to set rangeProvider: ${e.message}")
            }
        }
    }

    // Prepare entries for chart
    val entries = remember(data) {
        data.mapIndexed { index, entity ->
            com.patrykandpatrick.vico.core.entry.FloatEntry(
                x = index.toFloat(),
                y = entity.value.toFloat()
            )
        }
    }

    val chartEntryModel = remember(entries) {
        entryModelOf(entries)
    }

    val chartScrollState = rememberChartScrollState()

    // Get line color for the chart
    val lineColor = remember(category) {
        when (category) {
            GrowthCategory.weight -> Color(0xFF2196F3)
            GrowthCategory.height -> Color(0xFF4CAF50)
            GrowthCategory.head -> Color(0xFFFF9800)
        }
    }

    // Use Vico Compose API with custom y-axis bounds and fixed x-axis labels
    ProvideChartStyle(
        chartStyle = m3ChartStyle(
            entityColors = listOf(lineColor)
        )
    ) {
        Chart(
            chart = chart,
            model = chartEntryModel,
            modifier = modifier.padding(16.dp),
            startAxis = rememberStartAxis(
                valueFormatter = { value, _ ->
                    formatAxisValue(value.toDouble(), unit, category)
                }
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { value, _ ->
                    val index = value.toInt()
                    // Show only every other tick to halve the number
                    if (index >= 0 && index < data.size && index % 2 == 0) {
                        formatDateShort(data[index].ts)
                    } else {
                        ""
                    }
                },
                labelRotationDegrees = -45f // Rotate labels -45 degrees to prevent truncation
            ),
            chartScrollState = chartScrollState
        )
    }
}

fun formatAxisValue(value: Double, unit: String, category: GrowthCategory): String {
    return when {
        category == GrowthCategory.weight && unit == "lb" -> {
            val pounds = value.toInt()
            val ounces = ((value - pounds) * 16).toInt()
            if (ounces > 0) {
                "${pounds}lb ${ounces}oz"
            } else {
                "${pounds}lb"
            }
        }
        unit == "in" -> String.format("%.1f in", value)
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
