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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WordsGraphView(
    words: List<BabyWordEntity>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val sorted = remember(words) { words.sortedBy { it.ts } }
    if (sorted.isEmpty()) {
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

    val (yMin, yMax, tickStep) = remember(sorted) {
        val n = sorted.size
        val maxY = n.toDouble()
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
            if (index >= 0 && index < sorted.size) {
                val ts = sorted[index].ts
                val date = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalDate()
                date.format(dateFmt)
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

    val chartModel = remember(sorted) {
        val entries = sorted.mapIndexed { index, _ ->
            LineCartesianLayerModel.Entry(index.toFloat(), (index + 1).toFloat())
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
