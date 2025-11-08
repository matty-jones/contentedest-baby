package com.contentedest.baby.ui.growth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.contentedest.baby.data.local.GrowthCategory
import com.contentedest.baby.data.repo.GrowthRepository
import com.patrykandpatrick.vico.compose.axis.horizontal.bottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.startAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollState
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
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

    // Load data when category changes
    LaunchedEffect(selectedCategory) {
        growthData = growthRepository.getByCategory(selectedCategory)
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
                val previous = growthData.getOrNull(growthData.size - 2)
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
                        if (previous != null) {
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

    ProvideChartStyle(
        chartStyle = m3ChartStyle(
            entityColors = listOf(
                when (category) {
                    GrowthCategory.weight -> Color(0xFF2196F3)
                    GrowthCategory.height -> Color(0xFF4CAF50)
                    GrowthCategory.head -> Color(0xFFFF9800)
                }
            )
        )
    ) {
        Chart(
            chart = lineChart(),
            model = chartEntryModel,
            modifier = modifier.padding(16.dp),
            startAxis = startAxis(),
            bottomAxis = bottomAxis(
                valueFormatter = { value, _ ->
                    val index = value.toInt()
                    if (index >= 0 && index < data.size) {
                        formatDateShort(data[index].ts)
                    } else {
                        ""
                    }
                }
            ),
            chartScrollState = chartScrollState
        )
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
