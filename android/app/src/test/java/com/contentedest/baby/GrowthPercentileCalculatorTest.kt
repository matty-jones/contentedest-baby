package com.contentedest.baby

import com.contentedest.baby.data.local.GrowthCategory
import com.contentedest.baby.ui.growth.GrowthPercentileCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthPercentileCalculatorTest {
    @Test
    fun medianMeasurementsMapToNear50thPercentile() {
        val weightPercentile = GrowthPercentileCalculator.calculateWeightPercentile(
            weightKg = 3.3464,
            ageMonths = 0.0
        )
        val heightPercentile = GrowthPercentileCalculator.calculateHeightPercentile(
            heightCm = 49.8842,
            ageMonths = 0.0
        )

        assertNotNull(weightPercentile)
        assertNotNull(heightPercentile)
        assertEquals(50.0, weightPercentile!!, 0.2)
        assertEquals(50.0, heightPercentile!!, 0.2)
    }

    @Test
    fun weightPercentileIsConsistentAcrossUnits() {
        val ageMonths = 6.0
        val pounds = 17.5
        val kilograms = pounds * 0.45359237

        val fromLb = GrowthPercentileCalculator.calculatePercentile(
            value = pounds,
            unit = "lb",
            ageMonths = ageMonths,
            category = GrowthCategory.weight
        )
        val fromKg = GrowthPercentileCalculator.calculatePercentile(
            value = kilograms,
            unit = "kg",
            ageMonths = ageMonths,
            category = GrowthCategory.weight
        )

        assertNotNull(fromLb)
        assertNotNull(fromKg)
        assertEquals(fromKg!!, fromLb!!, 0.1)
    }

    @Test
    fun percentileValueRoundTripsForWeight() {
        val targetPercentile = 75.0
        val ageMonths = 12.0
        val valueKg = GrowthPercentileCalculator.calculatePercentileValue(
            percentile = targetPercentile,
            ageMonths = ageMonths,
            category = GrowthCategory.weight,
            unit = "kg"
        )
        assertNotNull(valueKg)

        val recalculated = GrowthPercentileCalculator.calculatePercentile(
            value = valueKg!!,
            unit = "kg",
            ageMonths = ageMonths,
            category = GrowthCategory.weight
        )
        assertNotNull(recalculated)
        assertTrue(recalculated!! in 74.0..76.0)
    }

    @Test
    fun unsupportedUnitsAndCategoriesReturnNull() {
        assertNull(
            GrowthPercentileCalculator.calculatePercentile(
                value = 10.0,
                unit = "stones",
                ageMonths = 8.0,
                category = GrowthCategory.weight
            )
        )
        assertNull(
            GrowthPercentileCalculator.calculatePercentile(
                value = 16.0,
                unit = "in",
                ageMonths = 8.0,
                category = GrowthCategory.head
            )
        )
    }
}
