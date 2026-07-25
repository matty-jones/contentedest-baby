package com.contentedest.baby.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer

/**
 * Shared Growth/Words percentile line fade and stroke styles.
 * Median (50th): RGB×0.90 alpha 0.75 dashed; quartiles RGB×0.85 alpha 0.65;
 * extremes RGB×0.80 alpha 0.55; main solid 3dp.
 */
data class PercentileLineColors(
    val main: Color,
    val median: Color,
    val quartile: Color,
    val extreme: Color
)

fun percentileLineColors(base: Color): PercentileLineColors {
    fun faded(multiplier: Float, alpha: Float): Color = Color(
        red = (base.red * multiplier).coerceIn(0f, 1f),
        green = (base.green * multiplier).coerceIn(0f, 1f),
        blue = (base.blue * multiplier).coerceIn(0f, 1f),
        alpha = alpha
    )
    return PercentileLineColors(
        main = base,
        median = faded(0.90f, 0.75f),
        quartile = faded(0.85f, 0.65f),
        extreme = faded(0.80f, 0.55f)
    )
}

data class PercentileChartLines(
    val main: LineCartesianLayer.Line,
    val median: LineCartesianLayer.Line,
    val quartile: LineCartesianLayer.Line,
    val extreme: LineCartesianLayer.Line
)

@Composable
fun rememberPercentileChartLines(baseColor: Color): PercentileChartLines {
    val colors = remember(baseColor) { percentileLineColors(baseColor) }
    val main = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(colors.main.toArgb())),
        stroke = LineCartesianLayer.LineStroke.Continuous(thicknessDp = 3f)
    )
    val median = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(colors.median.toArgb())),
        stroke = LineCartesianLayer.LineStroke.Dashed(
            thicknessDp = 2f,
            dashLengthDp = 8f,
            gapLengthDp = 4f
        )
    )
    val quartile = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(colors.quartile.toArgb())),
        stroke = LineCartesianLayer.LineStroke.Continuous(thicknessDp = 1.5f)
    )
    val extreme = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(colors.extreme.toArgb())),
        stroke = LineCartesianLayer.LineStroke.Continuous(thicknessDp = 1f)
    )
    return PercentileChartLines(
        main = main,
        median = median,
        quartile = quartile,
        extreme = extreme
    )
}
