package com.contentedest.baby.ui.words

import com.contentedest.baby.data.local.BabyWordEntity
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Fuzzy matching for vocabulary search and MA-B checklist hits.
 *
 * FuzzyKot was evaluated but ships Kotlin 2.3 metadata incompatible with this
 * app's Room/kapt (Kotlin 2.0.21). Levenshtein ratio matching covers typos,
 * casing, and spacing for short words without an external dependency.
 */
object WordFuzzyMatcher {
    /** Minimum score (0-100) for a fuzzy hit on short vocabulary strings. */
    const val DEFAULT_CUTOFF = 78

    fun normalize(raw: String): String {
        val decomposed = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKD)
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace('’', '\'')
            .replace(Regex("[^a-z0-9'\\s]"), " ")
            .replace("'", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun bestMatch(
        query: String,
        candidates: Collection<String>,
        cutoff: Int = DEFAULT_CUTOFF
    ): Pair<String, Int>? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty() || candidates.isEmpty()) return null

        var bestCandidate: String? = null
        var bestScore = -1
        for (candidate in candidates) {
            val normalizedCandidate = normalize(candidate)
            if (normalizedCandidate.isEmpty()) continue
            val score = if (normalizedCandidate == normalizedQuery) {
                100
            } else {
                ratio(normalizedQuery, normalizedCandidate)
            }
            if (score > bestScore) {
                bestScore = score
                bestCandidate = candidate
            }
        }
        return if (bestCandidate != null && bestScore >= cutoff) {
            bestCandidate to bestScore
        } else {
            null
        }
    }

    fun matchesMab(query: String, cutoff: Int = DEFAULT_CUTOFF): Boolean {
        return bestMatch(query, MacArthurBatesChecklist.WORDS, cutoff) != null
    }

    fun matchedMabWord(query: String, cutoff: Int = DEFAULT_CUTOFF): String? {
        return bestMatch(query, MacArthurBatesChecklist.WORDS, cutoff)?.first
    }

    fun findWordInList(
        query: String,
        words: List<BabyWordEntity>,
        cutoff: Int = DEFAULT_CUTOFF
    ): BabyWordEntity? {
        if (words.isEmpty()) return null
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return null

        words.firstOrNull { normalize(it.word) == normalizedQuery }?.let { return it }

        var best: BabyWordEntity? = null
        var bestScore = -1
        for (entity in words) {
            val score = ratio(normalizedQuery, normalize(entity.word))
            if (score > bestScore) {
                bestScore = score
                best = entity
            }
        }
        return if (best != null && bestScore >= cutoff) best else null
    }

    /** Levenshtein similarity as an integer percent 0–100. */
    fun ratio(a: String, b: String): Int {
        if (a == b) return 100
        if (a.isEmpty() || b.isEmpty()) return 0
        val distance = levenshtein(a, b)
        val maxLen = max(a.length, b.length)
        return ((1.0 - distance.toDouble() / maxLen) * 100.0).toInt()
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m

        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[n]
    }
}
