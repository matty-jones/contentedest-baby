package com.contentedest.baby

import com.contentedest.baby.data.repo.WordRepository
import com.contentedest.baby.ui.words.MacArthurBatesChecklist
import com.contentedest.baby.ui.words.VocabularyPercentileCalculator
import com.contentedest.baby.ui.words.WordFuzzyMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyPercentileCalculatorTest {
    @Test
    fun knotEndpointsMatchTable() {
        assertEquals(38.0, VocabularyPercentileCalculator.valueAtAge(50, 16.0)!!, 0.01)
        assertEquals(1175.0, VocabularyPercentileCalculator.valueAtAge(50, 30.0)!!, 0.01)
    }

    @Test
    fun outsideRangeReturnsNull() {
        assertNull(VocabularyPercentileCalculator.valueAtAge(50, 15.9))
        assertNull(VocabularyPercentileCalculator.valueAtAge(50, 30.1))
        assertNull(VocabularyPercentileCalculator.valueAtAge(50, 0.0))
    }

    @Test
    fun midMonthLogInterpolationIsBetweenKnots() {
        val mid = VocabularyPercentileCalculator.valueAtAge(50, 24.5)!!
        val low = VocabularyPercentileCalculator.valueAtAge(50, 24.0)!!
        val high = VocabularyPercentileCalculator.valueAtAge(50, 25.0)!!
        assertTrue(mid > low)
        assertTrue(mid < high)
    }
}

class WordFuzzyMatcherTest {
    @Test
    fun checklistHasExpectedSize() {
        assertEquals(89, MacArthurBatesChecklist.WORDS.size)
        assertEquals(89, MacArthurBatesChecklist.TOTAL)
    }

    @Test
    fun normalizeCollapsesCaseSpacingAndPunctuation() {
        assertEquals("uh oh", WordFuzzyMatcher.normalize("  UH   oh  "))
        assertEquals("dont", WordFuzzyMatcher.normalize("don’t"))
        assertEquals("dont", WordFuzzyMatcher.normalize("don't"))
    }

    @Test
    fun matchesMabExactAndFuzzy() {
        assertTrue(WordFuzzyMatcher.matchesMab("kitty"))
        assertTrue(WordFuzzyMatcher.matchesMab("KITTY"))
        assertTrue(WordFuzzyMatcher.matchesMab("kity"))
        assertFalse(WordFuzzyMatcher.matchesMab("xylophone"))
    }

    @Test
    fun saysImpliesUnderstands() {
        assertEquals(true to true, WordRepository.normalizeMabFlags(false, true))
        assertEquals(true to false, WordRepository.normalizeMabFlags(true, false))
        assertEquals(false to false, WordRepository.normalizeMabFlags(false, false))
    }

    @Test
    fun bestMatchReturnsTopCandidate() {
        val hit = WordFuzzyMatcher.bestMatch("balle", listOf("ball", "balloon", "wall"))
        assertNotNull(hit)
        assertEquals("ball", hit!!.first)
    }
}
