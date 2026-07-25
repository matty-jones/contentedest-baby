package com.contentedest.baby.ui.words

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.contentedest.baby.data.local.BabyWordEntity
import com.contentedest.baby.ui.growth.formatDate

@Composable
fun WordsListView(
    words: List<BabyWordEntity>,
    onWordClick: (BabyWordEntity) -> Unit,
    listState: LazyGridState,
    highlightedWordId: String? = null,
    modifier: Modifier = Modifier
) {
    if (words.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No words yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val defaultContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val highlightContainer = MaterialTheme.colorScheme.primaryContainer

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(words, key = { it.id }) { entry ->
            val highlighted = entry.id == highlightedWordId
            val containerColor by animateColorAsState(
                targetValue = if (highlighted) highlightContainer else defaultContainer,
                animationSpec = tween(durationMillis = 250),
                label = "wordHighlight"
            )
            Card(
                modifier = Modifier.clickable { onWordClick(entry) },
                colors = CardDefaults.cardColors(containerColor = containerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.word.displayWordTitleCase().ifEmpty { "—" },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        when {
                            entry.says -> Text(
                                text = "*",
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            entry.understands -> Text(
                                text = "*",
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = formatDate(entry.ts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
