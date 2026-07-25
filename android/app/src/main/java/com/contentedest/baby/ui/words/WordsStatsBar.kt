package com.contentedest.baby.ui.words

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentedest.baby.data.repo.WordRepository
import com.contentedest.baby.ui.growth.calculateAgeMonths
import com.contentedest.baby.ui.growth.dobEpochSeconds
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun WordsStatsBar(
    wordRepository: WordRepository,
    dobEpochDays: Int? = null,
    reloadToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var total by remember { mutableStateOf(0) }
    var understood by remember { mutableStateOf(0) }
    var said by remember { mutableStateOf(0) }
    var percentile by remember { mutableStateOf<Double?>(null) }

    fun refresh() {
        scope.launch {
            val words = wordRepository.getAllOrderedByFirstUseDesc()
            total = words.size
            val matchedUnderstood = mutableSetOf<String>()
            val matchedSaid = mutableSetOf<String>()
            for (word in words) {
                val mab = WordFuzzyMatcher.matchedMabWord(word.word) ?: continue
                val key = WordFuzzyMatcher.normalize(mab)
                if (word.understands) matchedUnderstood.add(key)
                if (word.says) matchedSaid.add(key)
            }
            understood = matchedUnderstood.size
            said = matchedSaid.size

            val dobTs = dobEpochSeconds(dobEpochDays)
            percentile = if (dobTs != null && total > 0) {
                val ageMonths = calculateAgeMonths(dobTs, System.currentTimeMillis() / 1000)
                VocabularyPercentileCalculator.percentileForCount(total.toDouble(), ageMonths)
            } else {
                null
            }
        }
    }

    LaunchedEffect(reloadToken, dobEpochDays) {
        refresh()
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000)
            refresh()
        }
    }

    val totalLabel = MacArthurBatesChecklist.TOTAL
    val text = when {
        total <= 0 -> "No words yet"
        else -> {
            val pctPart = percentile?.let {
                String.format(Locale.US, " (%.1fth %%ile)", it)
            }.orEmpty()
            "$total words$pctPart · MA-B: $understood/$totalLabel Understood, $said/$totalLabel Said"
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
