package com.contentedest.baby.ui.words

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentedest.baby.data.local.BabyWordEntity
import com.contentedest.baby.ui.chart.rememberPercentileChartLines
import com.contentedest.baby.ui.growth.calculateAgeMonths
import com.contentedest.baby.ui.growth.dobEpochSeconds
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun buildDailyCumulativeSeries(
    sortedWords: List<BabyWordEntity>,
    zone: ZoneId
): List<Pair<LocalDate, Float>> {
    if (sortedWords.isEmpty()) return emptyList()
    val firstDay = Instant.ofEpochSecond(sortedWords.first().ts).atZone(zone).toLocalDate()
    val lastWordDay = Instant.ofEpochSecond(sortedWords.last().ts).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val endDay = maxOf(lastWordDay, today)
    var wordIdx = 0
    val result = mutableListOf<Pair<LocalDate, Float>>()
    var d = firstDay
    while (!d.isAfter(endDay)) {
        val nextDayStart = d.plusDays(1).atStartOfDay(zone).toEpochSecond()
        while (wordIdx < sortedWords.size && sortedWords[wordIdx].ts < nextDayStart) {
            wordIdx++
        }
        result.add(d to wordIdx.toFloat())
        d = d.plusDays(1)
    }
    return result
}

@Composable
fun WordsGraphView(
    words: List<BabyWordEntity>,
    lineColor: Color,
    dobEpochDays: Int? = null,
    modifier: Modifier = Modifier
) {
    val zone = ZoneId.systemDefault()
    val sorted = remember(words) { words.sortedBy { it.ts } }
    val dailySeries = remember(sorted) {
        buildDailyCumulativeSeries(sorted, zone)
    }
    val dobTs = remember(dobEpochDays) { dobEpochSeconds(dobEpochDays, zone) }

    if (dailySeries.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No words to display",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val percentiles = listOf(5, 25, 50, 75, 95)
    val percentileSeries = remember(dailySeries, dobTs) {
        if (dobTs == null) {
            null
        } else {
            percentiles.map { pct ->
                dailySeries.mapIndexedNotNull { index, (day, _) ->
                    val dayTs = day.atStartOfDay(zone).toEpochSecond()
                    val ageMonths = calculateAgeMonths(dobTs, dayTs)
                    val value = VocabularyPercentileCalculator.valueAtAge(pct, ageMonths)
                        ?: return@mapIndexedNotNull null
                    LineCartesianLayerModel.Entry(index.toFloat(), value.toFloat())
                }
            }
        }
    }

    val (yMin, yMax, tickStep) = remember(dailySeries, percentileSeries) {
        val dataMax = dailySeries.maxOf { it.second }.toDouble()
        val pctMax = percentileSeries
            ?.flatten()
            ?.maxOfOrNull { it.y.toDouble() }
            ?: 0.0
        val maxY = maxOf(dataMax, pctMax)
        val padding = (maxY * 0.1).coerceAtLeast(1.0)
        val maxWithPad = maxY + padding
        val step = when {
            maxWithPad <= 5.0 -> 1.0
            maxWithPad <= 10.0 -> 2.0
            maxWithPad <= 20.0 -> 5.0
            maxWithPad <= 100.0 -> 10.0
            maxWithPad <= 500.0 -> 50.0
            else -> 100.0
        }
        Triple(0.0, maxWithPad, step)
    }

    val startAxis = VerticalAxis.rememberStart(
        label = rememberTextComponent(color = Color.White, textSize = 12.sp),
        valueFormatter = CartesianValueFormatter { _, value, _ ->
            value.toInt().toString()
        },
        itemPlacer = remember(tickStep) {
            VerticalAxis.ItemPlacer.step(step = { tickStep })
        }
    )

    val dateFmt = DateTimeFormatter.ofPattern("MM/dd")
    val bottomAxis = HorizontalAxis.rememberBottom(
        label = rememberTextComponent(color = Color.White, textSize = 12.sp),
        valueFormatter = CartesianValueFormatter { _, x, _ ->
            val index = x.toInt()
            if (index >= 0 && index < dailySeries.size) {
                dailySeries[index].first.format(dateFmt)
            } else {
                ""
            }
        },
        itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 2 }) }
    )

    val percentileLines = rememberPercentileChartLines(lineColor)

    val mainLineLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(listOf(percentileLines.main)),
        rangeProvider = CartesianLayerRangeProvider.fixed(minY = yMin, maxY = yMax)
    )
    val percentile50Layer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(listOf(percentileLines.median)),
        rangeProvider = CartesianLayerRangeProvider.fixed(minY = yMin, maxY = yMax)
    )
    val percentile25Layer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(listOf(percentileLines.quartile)),
        rangeProvider = CartesianLayerRangeProvider.fixed(minY = yMin, maxY = yMax)
    )
    val percentile75Layer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(listOf(percentileLines.quartile)),
        rangeProvider = CartesianLayerRangeProvider.fixed(minY = yMin, maxY = yMax)
    )
    val percentile5Layer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(listOf(percentileLines.extreme)),
        rangeProvider = CartesianLayerRangeProvider.fixed(minY = yMin, maxY = yMax)
    )
    val percentile95Layer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(listOf(percentileLines.extreme)),
        rangeProvider = CartesianLayerRangeProvider.fixed(minY = yMin, maxY = yMax)
    )

    val allLayers = remember(percentileSeries) {
        if (percentileSeries == null) {
            listOf(mainLineLayer)
        } else {
            val layers = mutableListOf<LineCartesianLayer>(mainLineLayer)
            // Order: main, 50th, 25th, 75th, 5th, 95th (indices in percentiles: 5,25,50,75,95)
            if (percentileSeries[2].isNotEmpty()) layers.add(percentile50Layer)
            if (percentileSeries[1].isNotEmpty()) layers.add(percentile25Layer)
            if (percentileSeries[3].isNotEmpty()) layers.add(percentile75Layer)
            if (percentileSeries[0].isNotEmpty()) layers.add(percentile5Layer)
            if (percentileSeries[4].isNotEmpty()) layers.add(percentile95Layer)
            layers
        }
    }

    val chart = rememberCartesianChart(
        *allLayers.toTypedArray(),
        startAxis = startAxis,
        bottomAxis = bottomAxis
    )

    val chartModel = remember(dailySeries, percentileSeries) {
        val mainEntries = dailySeries.mapIndexed { index, pair ->
            LineCartesianLayerModel.Entry(index.toFloat(), pair.second)
        }
        val models = mutableListOf<CartesianLayerModel>(LineCartesianLayerModel(listOf(mainEntries)))
        if (percentileSeries != null) {
            if (percentileSeries[2].isNotEmpty()) {
                models.add(LineCartesianLayerModel(listOf(percentileSeries[2])))
            }
            if (percentileSeries[1].isNotEmpty()) {
                models.add(LineCartesianLayerModel(listOf(percentileSeries[1])))
            }
            if (percentileSeries[3].isNotEmpty()) {
                models.add(LineCartesianLayerModel(listOf(percentileSeries[3])))
            }
            if (percentileSeries[0].isNotEmpty()) {
                models.add(LineCartesianLayerModel(listOf(percentileSeries[0])))
            }
            if (percentileSeries[4].isNotEmpty()) {
                models.add(LineCartesianLayerModel(listOf(percentileSeries[4])))
            }
        }
        CartesianChartModel(models = models)
    }

    val chartScrollState = rememberVicoScrollState(
        scrollEnabled = true,
        initialScroll = Scroll.Absolute.End,
        autoScroll = Scroll.Absolute.End,
        autoScrollCondition = AutoScrollCondition.OnModelGrowth,
    )

    Box(modifier = modifier) {
        CartesianChartHost(
            chart = chart,
            model = chartModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            scrollState = chartScrollState
        )
    }
}
