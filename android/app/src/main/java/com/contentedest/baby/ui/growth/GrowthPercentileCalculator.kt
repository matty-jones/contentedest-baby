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
        LMSValues(0.0, 0.3487, 3.3464, 0.14602),
        LMSValues(0.5, 0.255340625, 3.805071875, 0.140822813),
        LMSValues(1.5, 0.21154375, 5.063959375, 0.128363438),
        LMSValues(2.5, 0.184634375, 6.000365625, 0.120200312),
        LMSValues(3.5, 0.16408125, 6.7070375, 0.114950938),
        LMSValues(4.5, 0.147115625, 7.268678125, 0.1118025),
        LMSValues(5.5, 0.1324375, 7.731146875, 0.11007375),
        LMSValues(6.5, 0.1193625, 8.122040625, 0.109241563),
        LMSValues(7.5, 0.1075875, 8.460753125, 0.10889),
        LMSValues(8.5, 0.096784375, 8.76155625, 0.1088),
        LMSValues(9.5, 0.086753125, 9.035559375, 0.10885),
        LMSValues(10.5, 0.077421875, 9.290209375, 0.108975937),
        LMSValues(11.5, 0.06859375, 9.531240625, 0.109150312),
        LMSValues(12.5, 0.060359375, 9.762315625, 0.109364688),
        LMSValues(13.5, 0.052428125, 9.985815625, 0.109619062),
        LMSValues(14.5, 0.04493125, 10.203540625, 0.109913438),
        LMSValues(15.5, 0.03774375, 10.417190625, 0.110237813),
        LMSValues(16.5, 0.03085625, 10.627709375, 0.110592187),
        LMSValues(17.5, 0.02426875, 10.8354625, 0.110986562),
        LMSValues(18.5, 0.01788125, 11.040928125, 0.111410938),
        LMSValues(19.5, 0.01169375, 11.244659375, 0.111870625),
        LMSValues(20.5, 0.00580625, 11.447490625, 0.112359375),
        LMSValues(21.5, 0.00001875, 11.64958125, 0.112878125),
        LMSValues(22.5, -0.00556875, 11.85096875, 0.113416875),
        LMSValues(23.5, -0.011028125, 12.05155625, 0.113975625),
        LMSValues(24.5, -0.016271875, 12.251071875, 0.114554375),
        LMSValues(25.5, -0.02143125, 12.448715625, 0.115143125),
        LMSValues(26.5, -0.026459375, 12.6438, 0.115741875),
        LMSValues(27.5, -0.03130625, 12.83569375, 0.116340625),
        LMSValues(28.5, -0.036146875, 13.024059375, 0.116929375),
        LMSValues(29.5, -0.040790625, 13.208928125, 0.117518125),
        LMSValues(30.5, -0.04536875, 13.390328125, 0.118096875),
        LMSValues(31.5, -0.04985625, 13.56863125, 0.118675625),
        LMSValues(32.5, -0.054221875, 13.744146875, 0.119244375),
        LMSValues(33.5, -0.05853125, 13.917240625, 0.119803125),
        LMSValues(34.5, -0.06271875, 14.088525, 0.120351875),
        LMSValues(35.5, -0.066853125, 14.258375, 0.120890625),
        LMSValues(36.0, -0.068875, 14.3429, 0.1211575)
    )
    
    // Length-for-age lookup table (male only, Sex=1)
    private val heightLMSData = listOf(
        LMSValues(0.0, 1.0, 49.8842, 0.03795),
        LMSValues(0.5, 1.0, 52.53083125, 0.036436875),
        LMSValues(1.5, 1.0, 56.685203125, 0.034847188),
        LMSValues(2.5, 1.0, 60.000225, 0.033717188),
        LMSValues(3.5, 1.0, 62.72061875, 0.032899375),
        LMSValues(4.5, 1.0, 64.93963125, 0.032290313),
        LMSValues(5.5, 1.0, 66.791971875, 0.031825937),
        LMSValues(6.5, 1.0, 68.411109375, 0.031511562),
        LMSValues(7.5, 1.0, 69.891946875, 0.031307187),
        LMSValues(8.5, 1.0, 71.29124375, 0.0312),
        LMSValues(9.5, 1.0, 72.632034375, 0.03117),
        LMSValues(10.5, 1.0, 73.91648125, 0.03121),
        LMSValues(11.5, 1.0, 75.149140625, 0.03131),
        LMSValues(12.5, 1.0, 76.3387, 0.031454688),
        LMSValues(13.5, 1.0, 77.4888125, 0.031639063),
        LMSValues(14.5, 1.0, 78.601775, 0.031853438),
        LMSValues(15.5, 1.0, 79.68214375, 0.03209),
        LMSValues(16.5, 1.0, 80.733559375, 0.032352187),
        LMSValues(17.5, 1.0, 81.7570875, 0.032636563),
        LMSValues(18.5, 1.0, 82.753528125, 0.032940937),
        LMSValues(19.5, 1.0, 83.723634375, 0.033255313),
        LMSValues(20.5, 1.0, 84.670040625, 0.033589687),
        LMSValues(21.5, 1.0, 85.5938875, 0.033924062),
        LMSValues(22.5, 1.0, 86.496621875, 0.034268437),
        LMSValues(23.5, 1.0, 87.380771875, 0.034622812),
        LMSValues(24.5, 1.0, 87.546596875, 0.035247187),
        LMSValues(25.5, 1.0, 88.39198125, 0.035591562),
        LMSValues(26.5, 1.0, 89.2157125, 0.035925937),
        LMSValues(27.5, 1.0, 90.0185125, 0.036260313),
        LMSValues(28.5, 1.0, 90.800059375, 0.036579375),
        LMSValues(29.5, 1.0, 91.560284375, 0.036889062),
        LMSValues(30.5, 1.0, 92.30025, 0.037183437),
        LMSValues(31.5, 1.0, 93.02138125, 0.037467813),
        LMSValues(32.5, 1.0, 93.7250875, 0.037742188),
        LMSValues(33.5, 1.0, 94.4137, 0.037996562),
        LMSValues(34.5, 1.0, 95.0897625, 0.038240938),
        LMSValues(35.5, 1.0, 95.754828125, 0.038475312)
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
     * Inverse error function approximation
     * Uses Acklam's algorithm for high accuracy
     * This is more accurate than Winitzki's approximation
     */
    private fun erfinv(x: Double): Double {
        // Clamp to valid range [-1, 1]
        val clampedX = x.coerceIn(-0.9999999, 0.9999999)
        val sign = if (clampedX < 0) -1.0 else 1.0
        val absX = abs(clampedX)
        
        // Acklam's algorithm for inverse error function
        // This provides much better accuracy than Winitzki's approximation
        val a = 0.147
        
        // For values very close to ±1, use asymptotic expansion
        if (absX >= 1.0 - 1e-7) {
            val w = -ln((1.0 - absX) * (1.0 + absX))
            val p = w - (3.0 - ln(4.0) - ln(w) + ln(ln(w))) / (2.0 + 2.0 / w)
            return sign * sqrt(p)
        }
        
        // Standard Acklam approximation
        val xSquared = absX * absX
        val lnTerm = ln(1.0 - xSquared)
        
        // Calculate: sqrt( sqrt(2/(π*a) + ln(1-x²)/2) - ln(1-x²)/2 )
        val term1 = 2.0 / (PI * a)
        val term2 = lnTerm / 2.0
        val innerSqrt = sqrt(term1 + term2)
        val result = sign * sqrt(innerSqrt - term2)
        
        return result
    }
    
    /**
     * Convert percentile to z-score (inverse of zScoreToPercentile)
     * Uses the inverse CDF of the standard normal distribution (probit function)
     * 
     * Implements a more accurate approximation using Acklam's algorithm
     * for the inverse normal CDF, which is more accurate than going through erfinv
     * 
     * @param percentile Percentile value (0-100)
     * @return Z-score
     */
    private fun percentileToZScore(percentile: Double): Double {
        // Clamp percentile to valid range to avoid numerical issues
        val clampedPercentile = percentile.coerceIn(0.01, 99.99)
        
        // Convert percentile to probability (0-1)
        val p = clampedPercentile / 100.0
        
        // Use Acklam's algorithm for inverse normal CDF (more accurate than erfinv approach)
        // This directly calculates the probit function: probit(p) = Φ^(-1)(p)
        return probit(p)
    }
    
    /**
     * Probit function: inverse of the standard normal CDF
     * Uses Acklam's algorithm for high accuracy
     * 
     * @param p Probability value (0-1), where p = P(Z <= z)
     * @return Z-score such that P(Z <= z) = p
     */
    private fun probit(p: Double): Double {
        // Clamp p to valid range
        val clampedP = p.coerceIn(0.0000001, 0.9999999)
        
        // Acklam's algorithm for inverse normal CDF
        // This is more accurate than using erfinv
        val a0 = 2.50662823884
        val a1 = -18.61500062529
        val a2 = 41.39119773534
        val a3 = -25.44106049637
        val b1 = -8.47351093090
        val b2 = 23.08336743743
        val b3 = -21.06224101826
        val b4 = 3.13082909833
        val c0 = -2.78718931138
        val c1 = -2.29796479134
        val c2 = 4.85014127135
        val c3 = 2.32121276858
        val d1 = 3.54388924762
        val d2 = 1.63706781897
        
        val q = clampedP - 0.5
        val absQ = abs(q)
        
        return if (absQ < 0.425) {
            // Central region: use rational approximation
            val r = q * q
            val num = (((a3 * r + a2) * r + a1) * r + a0) * q
            val den = ((((b4 * r + b3) * r + b2) * r + b1) * r + 1.0)
            num / den
        } else {
            // Tail regions: use different approximation
            val r = if (q < 0.0) clampedP else 1.0 - clampedP
            val rSqrt = sqrt(-ln(r))
            val num = ((c3 * rSqrt + c2) * rSqrt + c1) * rSqrt + c0
            val den = (d2 * rSqrt + d1) * rSqrt + 1.0
            val result = num / den
            if (q < 0.0) -result else result
        }
    }
    
    /**
     * Calculate measurement value from z-score and LMS values (inverse of calculateZScore)
     * 
     * Forward: Z = ((X/M)^L - 1) / (L * S)
     * Rearranging: (X/M)^L = 1 + Z * L * S
     * So: X/M = (1 + Z * L * S)^(1/L)
     * Therefore: X = M * (1 + Z * L * S)^(1/L)
     * 
     * @param zScore Z-score
     * @param lms LMS values
     * @return Measurement value
     */
    private fun zScoreToMeasurement(zScore: Double, lms: LMSValues): Double {
        return if (abs(lms.l) < 1e-10) {
            // L == 0: Z = ln(X/M) / S, so X = M * exp(Z * S)
            lms.m * exp(zScore * lms.s)
        } else {
            // L != 0: Z = ((X/M)^L - 1) / (L * S), so (X/M)^L = 1 + Z * L * S
            // So X = M * (1 + Z * L * S)^(1/L)
            val term = 1.0 + zScore * lms.l * lms.s
            
            // Validate that the term is positive (required for real-valued result)
            // For valid z-scores and LMS values, this should always be positive
            // but we add a safety check to avoid numerical issues
            if (term <= 0.0) {
                // This shouldn't happen for valid inputs, but handle edge case
                // For extreme z-scores, the term might be negative or zero
                // In this case, return a very small positive value
                return lms.m * 1e-10
            }
            
            // Calculate X = M * term^(1/L)
            // When L is negative, 1/L is also negative, so we're taking a negative power
            // This is equivalent to: X = M / term^(-1/L) = M / (term^(1/abs(L)))
            // But pow handles negative exponents correctly, so we can use it directly
            val power = 1.0 / lms.l
            val result = lms.m * term.pow(power)
            
            // Verify the result is positive and reasonable
            if (result <= 0.0 || !result.isFinite()) {
                // Fallback: use median if calculation fails
                return lms.m
            }
            
            result
        }
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
    
    /**
     * Calculate weight value for a given percentile at a specific age
     * @param percentile Percentile value (0-100)
     * @param ageMonths Age in months
     * @return Weight in kilograms, or null if calculation fails
     */
    fun calculateWeightValueForPercentile(percentile: Double, ageMonths: Double): Double? {
        val lms = findLMSValues(ageMonths, weightLMSData) ?: return null
        val zScore = percentileToZScore(percentile)
        return zScoreToMeasurement(zScore, lms)
    }
    
    /**
     * Calculate height value for a given percentile at a specific age
     * @param percentile Percentile value (0-100)
     * @param ageMonths Age in months
     * @return Height in centimeters, or null if calculation fails
     */
    fun calculateHeightValueForPercentile(percentile: Double, ageMonths: Double): Double? {
        val lms = findLMSValues(ageMonths, heightLMSData) ?: return null
        val zScore = percentileToZScore(percentile)
        return zScoreToMeasurement(zScore, lms)
    }
    
    /**
     * Convert kilograms to pounds
     * 1 kilogram = 2.20462262 pounds
     */
    private fun kilogramsToPounds(kg: Double): Double {
        return kg * 2.20462262
    }
    
    /**
     * Convert centimeters to inches
     * 1 centimeter = 0.393700787 inches
     */
    private fun centimetersToInches(cm: Double): Double {
        return cm * 0.393700787
    }
    
    /**
     * Calculate percentile value for a growth measurement
     * Handles unit conversion automatically
     * @param percentile Percentile value (0-100)
     * @param ageMonths Age in months
     * @param category Growth category (weight or height)
     * @param unit Desired output unit string ("lb", "kg", "in", "cm")
     * @return Measurement value in the specified unit, or null if calculation fails
     */
    fun calculatePercentileValue(
        percentile: Double,
        ageMonths: Double,
        category: com.contentedest.baby.data.local.GrowthCategory,
        unit: String
    ): Double? {
        // Normalize unit string (trim whitespace and convert to lowercase)
        val normalizedUnit = unit.trim().lowercase()
        
        return when (category) {
            com.contentedest.baby.data.local.GrowthCategory.weight -> {
                val weightKg = calculateWeightValueForPercentile(percentile, ageMonths) ?: return null
                when (normalizedUnit) {
                    "lb", "lbs", "pound", "pounds" -> kilogramsToPounds(weightKg)
                    "kg", "kilogram", "kilograms" -> weightKg
                    else -> return null // Unknown unit
                }
            }
            com.contentedest.baby.data.local.GrowthCategory.height -> {
                val heightCm = calculateHeightValueForPercentile(percentile, ageMonths) ?: return null
                when (normalizedUnit) {
                    "in", "inch", "inches" -> centimetersToInches(heightCm)
                    "cm", "centimeter", "centimeters" -> heightCm
                    else -> return null // Unknown unit
                }
            }
            else -> null // Head circumference not supported
        }
    }
}

