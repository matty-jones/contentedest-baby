package com.contentedest.baby.ui.growth

import kotlin.math.*

/**
 * Data class to hold LMS values for a specific age
 */
data class LMSValues(
    val ageMonths: Double,
    val l: Double,
    val m: Double,
    val s: Double
)

/**
 * Utility class for calculating growth percentiles using WHO growth charts
 * Based on LMS (Lambda-Mu-Sigma) method
 */
object GrowthPercentileCalculator {
    
    // Weight-for-age lookup table (male only, Sex=1)
    // Agemos values represent half-month points (0 = 0-0.99 months, 0.5 = 0.5-1.49 months, etc.)
    private val weightLMSData = listOf(
        LMSValues(0.0, 1.815151075, 3.530203168, 0.152385273),
        LMSValues(0.5, 1.547523128, 4.003106424, 0.146025021),
        LMSValues(1.5, 1.068795548, 4.879525083, 0.136478767),
        LMSValues(2.5, 0.695973505, 5.672888765, 0.129677511),
        LMSValues(3.5, 0.41981509, 6.391391982, 0.124717085),
        LMSValues(4.5, 0.219866801, 7.041836432, 0.121040119),
        LMSValues(5.5, 0.077505598, 7.630425182, 0.1182712),
        LMSValues(6.5, -0.02190761, 8.162951035, 0.116153695),
        LMSValues(7.5, -0.0894409, 8.644832479, 0.114510349),
        LMSValues(8.5, -0.1334091, 9.081119817, 0.113217163),
        LMSValues(9.5, -0.1600954, 9.476500305, 0.11218624),
        LMSValues(10.5, -0.17429685, 9.835307701, 0.111354536),
        LMSValues(11.5, -0.1797189, 10.16153567, 0.110676413),
        LMSValues(12.5, -0.179254, 10.45885399, 0.110118635),
        LMSValues(13.5, -0.17518447, 10.7306256, 0.109656941),
        LMSValues(14.5, -0.16932268, 10.97992482, 0.109273653),
        LMSValues(15.5, -0.1631139, 11.20955529, 0.10895596),
        LMSValues(16.5, -0.15770999, 11.4220677, 0.108694678),
        LMSValues(17.5, -0.15402279, 11.61977698, 0.108483324),
        LMSValues(18.5, -0.15276214, 11.80477902, 0.108317416),
        LMSValues(19.5, -0.15446658, 11.9789663, 0.108193944),
        LMSValues(20.5, -0.15952202, 12.14404334, 0.108110954),
        LMSValues(21.5, -0.16817926, 12.30154103, 0.108067236),
        LMSValues(22.5, -0.1805668, 12.45283028, 0.108062078),
        LMSValues(23.5, -0.19670196, 12.59913494, 0.108095077),
        LMSValues(24.5, -0.21650121, 12.74154396, 0.108166005),
        LMSValues(25.5, -0.23979048, 12.88102276, 0.108274705),
        LMSValues(26.5, -0.26631585, 13.01842382, 0.108421024),
        LMSValues(27.5, -0.29575496, 13.1544966, 0.108604769),
        LMSValues(28.5, -0.32772936, 13.28989667, 0.108825681),
        LMSValues(29.5, -0.36181746, 13.42519408, 0.109083423),
        LMSValues(30.5, -0.39756808, 13.56088113, 0.109377581),
        LMSValues(31.5, -0.43452025, 13.69737858, 0.109707646),
        LMSValues(32.5, -0.47218875, 13.83504622, 0.110073084),
        LMSValues(33.5, -0.51012309, 13.97418199, 0.110473238),
        LMSValues(34.5, -0.54788557, 14.1150324, 0.1109074),
        LMSValues(35.5, -0.5850701, 14.25779618, 0.111374787),
        LMSValues(36.0, -0.60333785, 14.32994444, 0.111620652)
    )
    
    // Length-for-age lookup table (male only, Sex=1)
    private val heightLMSData = listOf(
        LMSValues(0.0, 1.267004226, 49.98888408, 0.053112191),
        LMSValues(0.5, 0.511237696, 52.6959753, 0.048692684),
        LMSValues(1.5, -0.45224446, 56.62842855, 0.04411683),
        LMSValues(2.5, -0.990594599, 59.60895343, 0.041795583),
        LMSValues(3.5, -1.285837689, 62.07700027, 0.040454126),
        LMSValues(4.5, -1.43031238, 64.2168641, 0.039633879),
        LMSValues(5.5, -1.47657547, 66.1253149, 0.039123813),
        LMSValues(6.5, -1.456837849, 67.8601799, 0.038811994),
        LMSValues(7.5, -1.391898768, 69.45908458, 0.038633209),
        LMSValues(8.5, -1.29571459, 70.94803912, 0.038546833),
        LMSValues(9.5, -1.177919048, 72.34586111, 0.038526262),
        LMSValues(10.5, -1.045326049, 73.6666541, 0.038553387),
        LMSValues(11.5, -0.902800887, 74.92129717, 0.038615501),
        LMSValues(12.5, -0.753908107, 76.11837536, 0.038703461),
        LMSValues(13.5, -0.601263523, 77.26479911, 0.038810557),
        LMSValues(14.5, -0.446805039, 78.36622309, 0.038931784),
        LMSValues(15.5, -0.291974772, 79.4273405, 0.039063356),
        LMSValues(16.5, -0.13784767, 80.45209492, 0.039202382),
        LMSValues(17.5, 0.014776155, 81.44383603, 0.039346629),
        LMSValues(18.5, 0.165304169, 82.40543643, 0.039494365),
        LMSValues(19.5, 0.313301809, 83.33938063, 0.039644238),
        LMSValues(20.5, 0.458455471, 84.24783394, 0.039795189),
        LMSValues(21.5, 0.600544631, 85.13269658, 0.039946388),
        LMSValues(22.5, 0.739438953, 85.9956488, 0.040097181),
        LMSValues(23.5, 0.875000447, 86.8381751, 0.04024706),
        LMSValues(24.5, 1.00720807, 87.66160934, 0.040395626),
        LMSValues(25.5, 0.837251351, 88.45247282, 0.040577525),
        LMSValues(26.5, 0.681492975, 89.22326434, 0.040723122),
        LMSValues(27.5, 0.538779654, 89.97549228, 0.040833194),
        LMSValues(28.5, 0.407697153, 90.71040853, 0.040909059),
        LMSValues(29.5, 0.286762453, 91.42907762, 0.040952433),
        LMSValues(30.5, 0.174489485, 92.13242379, 0.04096533),
        LMSValues(31.5, 0.069444521, 92.82127167, 0.040949976),
        LMSValues(32.5, -0.029720564, 93.49637946, 0.040908737),
        LMSValues(33.5, -0.124251789, 94.15846546, 0.040844062),
        LMSValues(34.5, -0.215288396, 94.80822923, 0.040758431),
        LMSValues(35.5, -0.30385434, 95.44636981, 0.040654312)
    )
    
    /**
     * Find LMS values for a given age in months using linear interpolation
     */
    private fun findLMSValues(ageMonths: Double, data: List<LMSValues>): LMSValues? {
        if (data.isEmpty()) return null
        
        // Clamp age to valid range
        val clampedAge = ageMonths.coerceIn(0.0, data.last().ageMonths)
        
        // If at or below minimum, return first value
        if (clampedAge <= data.first().ageMonths) {
            return data.first()
        }
        
        // If at or above maximum, return last value
        if (clampedAge >= data.last().ageMonths) {
            return data.last()
        }
        
        // Find the two closest data points for interpolation
        var lowerIndex = 0
        var upperIndex = data.size - 1
        
        // Find the lower bound (largest age <= clampedAge)
        for (i in data.indices) {
            if (data[i].ageMonths <= clampedAge) {
                lowerIndex = i
            } else {
                break
            }
        }
        
        // Find the upper bound (smallest age >= clampedAge)
        for (i in lowerIndex until data.size) {
            if (data[i].ageMonths >= clampedAge) {
                upperIndex = i
                break
            }
        }
        
        // If exact match, return that value
        if (lowerIndex == upperIndex) {
            return data[lowerIndex]
        }
        
        // Linear interpolation
        val lower = data[lowerIndex]
        val upper = data[upperIndex]
        val t = (clampedAge - lower.ageMonths) / (upper.ageMonths - lower.ageMonths)
        
        return LMSValues(
            ageMonths = clampedAge,
            l = lower.l + (upper.l - lower.l) * t,
            m = lower.m + (upper.m - lower.m) * t,
            s = lower.s + (upper.s - lower.s) * t
        )
    }
    
    /**
     * Calculate z-score from measurement and LMS values
     */
    private fun calculateZScore(measurement: Double, lms: LMSValues): Double {
        return if (abs(lms.l) < 1e-10) {
            // L == 0: Z = ln(X/M) / S
            ln(measurement / lms.m) / lms.s
        } else {
            // L != 0: Z = ((X/M)^L - 1) / (L * S)
            ((measurement / lms.m).pow(lms.l) - 1.0) / (lms.l * lms.s)
        }
    }
    
    /**
     * Convert z-score to percentile using cumulative distribution function
     * Approximation of the standard normal CDF
     */
    private fun zScoreToPercentile(z: Double): Double {
        // Using error function approximation for standard normal CDF
        // P(Z <= z) = 0.5 * (1 + erf(z / sqrt(2)))
        val result = 0.5 * (1.0 + erf(z / sqrt(2.0)))
        return result * 100.0
    }
    
    /**
     * Error function approximation using Abramowitz and Stegun formula
     */
    private fun erf(x: Double): Double {
        val a1 = 0.254829592
        val a2 = -0.284496736
        val a3 = 1.421413741
        val a4 = -1.453152027
        val a5 = 1.061405429
        val p = 0.3275911
        
        val sign = if (x < 0) -1.0 else 1.0
        val absX = abs(x)
        
        val t = 1.0 / (1.0 + p * absX)
        val y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * exp(-absX * absX)
        
        return sign * y
    }
    
    /**
     * Convert weight from pounds to kilograms
     * 1 pound = 0.45359237 kilograms (exact conversion)
     */
    private fun poundsToKilograms(lb: Double): Double {
        return lb * 0.45359237
    }
    
    /**
     * Convert length from inches to centimeters
     * 1 inch = 2.54 centimeters (exact conversion)
     */
    private fun inchesToCentimeters(inches: Double): Double {
        return inches * 2.54
    }
    
    /**
     * Calculate percentile for weight
     * @param weightKg Weight in kilograms
     * @param ageMonths Age in months
     * @return Percentile (0-100) or null if calculation fails
     */
    fun calculateWeightPercentile(weightKg: Double, ageMonths: Double): Double? {
        val lms = findLMSValues(ageMonths, weightLMSData) ?: return null
        val zScore = calculateZScore(weightKg, lms)
        return zScoreToPercentile(zScore)
    }
    
    /**
     * Calculate percentile for height/length
     * @param heightCm Height in centimeters
     * @param ageMonths Age in months
     * @return Percentile (0-100) or null if calculation fails
     */
    fun calculateHeightPercentile(heightCm: Double, ageMonths: Double): Double? {
        val lms = findLMSValues(ageMonths, heightLMSData) ?: return null
        val zScore = calculateZScore(heightCm, lms)
        return zScoreToPercentile(zScore)
    }
    
    /**
     * Calculate percentile for a growth measurement
     * Handles unit conversion automatically
     * @param value Measurement value
     * @param unit Unit string ("lb", "kg", "in", "cm")
     * @param ageMonths Age in months
     * @param category Growth category (weight or height)
     * @return Percentile (0-100) or null if calculation fails
     */
    fun calculatePercentile(
        value: Double,
        unit: String,
        ageMonths: Double,
        category: com.contentedest.baby.data.local.GrowthCategory
    ): Double? {
        // Normalize unit string (trim whitespace and convert to lowercase)
        val normalizedUnit = unit.trim().lowercase()
        
        return when (category) {
            com.contentedest.baby.data.local.GrowthCategory.weight -> {
                val weightKg = when (normalizedUnit) {
                    "lb", "lbs", "pound", "pounds" -> poundsToKilograms(value)
                    "kg", "kilogram", "kilograms" -> value
                    else -> return null // Unknown unit
                }
                calculateWeightPercentile(weightKg, ageMonths)
            }
            com.contentedest.baby.data.local.GrowthCategory.height -> {
                val heightCm = when (normalizedUnit) {
                    "in", "inch", "inches" -> inchesToCentimeters(value)
                    "cm", "centimeter", "centimeters" -> value
                    else -> return null // Unknown unit
                }
                calculateHeightPercentile(heightCm, ageMonths)
            }
            else -> null // Head circumference not supported
        }
    }
}

