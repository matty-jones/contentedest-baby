package com.contentedest.baby.ui.growth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentedest.baby.data.repo.GrowthRepository
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun GrowthStatsBar(
    growthRepository: GrowthRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var firstEntryTs by remember { mutableStateOf<Long?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Load first entry timestamp
    LaunchedEffect(Unit) {
        scope.launch {
            val allData = growthRepository.getAll()
            firstEntryTs = allData.firstOrNull()?.ts
        }
    }

    // Refresh data from database periodically (every 30 seconds)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000)
            scope.launch {
                val allData = growthRepository.getAll()
                firstEntryTs = allData.firstOrNull()?.ts
            }
        }
    }

    // Update time display every second for smooth updates
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            refreshTrigger++
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
        if (firstEntryTs != null) {
            // refreshTrigger causes recomposition, which recalculates the time
            val timeSince = formatTimeSince(firstEntryTs!!)
            Text(
                text = timeSince,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            Text(
                text = "No data",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

private fun formatTimeSince(firstTs: Long): String {
    val now = Instant.now().epochSecond
    var remainingSeconds = now - firstTs
    
    if (remainingSeconds < 0) return "0 days"
    
    // Calculate each unit
    val years = remainingSeconds / (365 * 24 * 3600)
    remainingSeconds %= (365 * 24 * 3600)
    
    val months = remainingSeconds / (30 * 24 * 3600) // Approximate: 30 days per month
    remainingSeconds %= (30 * 24 * 3600)
    
    val weeks = remainingSeconds / (7 * 24 * 3600)
    remainingSeconds %= (7 * 24 * 3600)
    
    val days = remainingSeconds / (24 * 3600)
    
    val parts = mutableListOf<String>()
    
    if (years > 0) {
        parts.add("$years year${if (years != 1L) "s" else ""}")
    }
    if (months > 0) {
        parts.add("$months month${if (months != 1L) "s" else ""}")
    }
    if (weeks > 0) {
        parts.add("$weeks week${if (weeks != 1L) "s" else ""}")
    }
    if (days > 0 || parts.isEmpty()) {
        parts.add("$days day${if (days != 1L) "s" else ""}")
    }
    
    return parts.joinToString(", ")
}

