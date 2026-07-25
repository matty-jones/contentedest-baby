package com.contentedest.baby.ui.words

import kotlin.math.expm1
import kotlin.math.ln1p

/**
 * Estimated total expressive vocabulary percentiles for boys (American English),
 * ages 16–30 months. Interpolate in log1p(word_count) space; null outside range.
 *
 * Source: Mayor & Plunkett (2011), with app-facing p05/p95 tail extrapolations.
 */
object VocabularyPercentileCalculator {
    const val MIN_AGE_MONTHS = 16.0
    const val MAX_AGE_MONTHS = 30.0

    data class Knot(
        val ageMonths: Double,
        val p05: Int,
        val p25: Int,
        val p50: Int,
        val p75: Int,
        val p95: Int
    )

    private val knots = listOf(
        Knot(16.0, 9, 14, 38, 96, 158),
        Knot(17.0, 9, 21, 48, 119, 287),
        Knot(18.0, 10, 32, 66, 162, 455),
        Knot(19.0, 14, 48, 96, 229, 645),
        Knot(20.0, 19, 69, 140, 325, 839),
        Knot(21.0, 26, 95, 199, 446, 1024),
        Knot(22.0, 32, 124, 274, 582, 1192),
        Knot(23.0, 38, 155, 357, 715, 1345),
        Knot(24.0, 41, 185, 443, 824, 1486),
        Knot(25.0, 44, 216, 522, 900, 1622),
        Knot(26.0, 47, 249, 594, 947, 1758),
        Knot(27.0, 53, 293, 666, 986, 1895),
        Knot(28.0, 71, 363, 755, 1055, 2029),
        Knot(29.0, 123, 490, 899, 1214, 2147),
        Knot(30.0, 308, 756, 1175, 1589, 2229)
    )

    fun valueAtAge(percentile: Int, ageMonths: Double): Double? {
        if (ageMonths < MIN_AGE_MONTHS || ageMonths > MAX_AGE_MONTHS) return null
        val getter: (Knot) -> Int = when (percentile) {
            5 -> Knot::p05
            25 -> Knot::p25
            50 -> Knot::p50
            75 -> Knot::p75
            95 -> Knot::p95
            else -> return null
        }

        if (ageMonths == MIN_AGE_MONTHS) return getter(knots.first()).toDouble()
        if (ageMonths == MAX_AGE_MONTHS) return getter(knots.last()).toDouble()

        val upperIndex = knots.indexOfFirst { it.ageMonths >= ageMonths }
        if (upperIndex < 0) return null
        if (upperIndex == 0) return getter(knots.first()).toDouble()

        val lower = knots[upperIndex - 1]
        val upper = knots[upperIndex]
        if (upper.ageMonths == lower.ageMonths) return getter(lower).toDouble()

        val t = (ageMonths - lower.ageMonths) / (upper.ageMonths - lower.ageMonths)
        val logLower = ln1p(getter(lower).toDouble())
        val logUpper = ln1p(getter(upper).toDouble())
        return expm1(logLower + t * (logUpper - logLower))
    }

    /**
     * Map an observed word count to a percentile at [ageMonths] by linear interpolation
     * between the 5/25/50/75/95 reference counts (themselves age-interpolated in log1p space).
     * Returns null outside the valid age window.
     */
    fun percentileForCount(wordCount: Double, ageMonths: Double): Double? {
        if (ageMonths < MIN_AGE_MONTHS || ageMonths > MAX_AGE_MONTHS) return null
        if (wordCount < 0) return null

        val bands = listOf(5, 25, 50, 75, 95).map { pct ->
            val y = valueAtAge(pct, ageMonths) ?: return null
            pct.toDouble() to y
        }

        // Exact hits
        bands.firstOrNull { kotlin.math.abs(it.second - wordCount) < 1e-9 }?.let { return it.first }

        // Below lowest / above highest: linear extrapolation from nearest segment
        if (wordCount <= bands.first().second) {
            val (p0, y0) = bands[0]
            val (p1, y1) = bands[1]
            return interpolatePercentile(wordCount, y0, y1, p0, p1)
        }
        if (wordCount >= bands.last().second) {
            val (p0, y0) = bands[bands.lastIndex - 1]
            val (p1, y1) = bands.last()
            return interpolatePercentile(wordCount, y0, y1, p0, p1)
        }

        for (i in 0 until bands.lastIndex) {
            val (p0, y0) = bands[i]
            val (p1, y1) = bands[i + 1]
            if (wordCount in y0..y1 || wordCount in y1..y0) {
                return interpolatePercentile(wordCount, y0, y1, p0, p1)
            }
        }
        return null
    }

    private fun interpolatePercentile(
        count: Double,
        y0: Double,
        y1: Double,
        p0: Double,
        p1: Double
    ): Double {
        if (kotlin.math.abs(y1 - y0) < 1e-9) return p0
        val t = (count - y0) / (y1 - y0)
        return p0 + t * (p1 - p0)
    }
}
