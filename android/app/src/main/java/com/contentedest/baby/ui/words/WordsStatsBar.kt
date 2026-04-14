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
import kotlinx.coroutines.launch

@Composable
fun WordsStatsBar(
    wordRepository: WordRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var total by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        scope.launch {
            total = wordRepository.getAllOrderedByFirstUseDesc().size
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000)
            scope.launch {
                total = wordRepository.getAllOrderedByFirstUseDesc().size
            }
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
            text = if (total > 0) "$total words" else "No words yet",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
