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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentedest.baby.data.local.BabyWordEntity
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
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
    modifier: Modifier = Modifier
) {
    val sorted = remember(words) { words.sortedBy { it.ts } }
    val dailySeries = remember(sorted) {
        buildDailyCumulativeSeries(sorted, ZoneId.systemDefault())
    }

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

    val (yMin, yMax, tickStep) = remember(dailySeries) {
        val maxY = dailySeries.maxOf { it.second }.toDouble()
        val padding = (maxY * 0.1).coerceAtLeast(1.0)
        val minY = 0.0
        val maxWithPad = maxY + padding
        val step = when {
            maxWithPad <= 5.0 -> 1.0
            maxWithPad <= 10.0 -> 2.0
            maxWithPad <= 20.0 -> 5.0
            else -> 10.0
        }
        Triple(minY, maxWithPad, step)
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

    val mainLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(lineColor.toArgb())),
        stroke = LineCartesianLayer.LineStroke.Continuous(thicknessDp = 3f)
    )

    val mainLineLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(listOf(mainLine)),
        rangeProvider = CartesianLayerRangeProvider.fixed(
            minY = yMin,
            maxY = yMax
        )
    )

    val chart = rememberCartesianChart(
        mainLineLayer,
        startAxis = startAxis,
        bottomAxis = bottomAxis
    )

    val chartModel = remember(dailySeries) {
        val entries = dailySeries.mapIndexed { index, pair ->
            LineCartesianLayerModel.Entry(index.toFloat(), pair.second)
        }
        val model = LineCartesianLayerModel(listOf(entries))
        CartesianChartModel(models = listOf(model))
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
