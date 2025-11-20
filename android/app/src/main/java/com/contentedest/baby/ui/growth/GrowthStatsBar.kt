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
import java.time.LocalDate
import java.time.ZoneId
import java.time.Period

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
    val firstInstant = Instant.ofEpochSecond(firstTs)
    val nowInstant = Instant.ofEpochSecond(now)
    
    // Convert to LocalDate in system timezone
    val firstDate = firstInstant.atZone(ZoneId.systemDefault()).toLocalDate()
    val nowDate = nowInstant.atZone(ZoneId.systemDefault()).toLocalDate()
    
    if (nowDate.isBefore(firstDate)) return "0 days"
    
    // Calculate period difference (gives us years, months, and days)
    val period = Period.between(firstDate, nowDate)
    
    // Calculate total months (years * 12 + months)
    val totalMonths = period.years * 12 + period.months
    
    // Get remaining days from the period
    var remainingDays = period.days.toLong()
    
    // Calculate weeks from remaining days
    val weeks = remainingDays / 7
    remainingDays %= 7
    
    val parts = mutableListOf<String>()
    
    if (totalMonths > 0) {
        parts.add("$totalMonths month${if (totalMonths != 1) "s" else ""}")
    }
    if (weeks > 0) {
        parts.add("$weeks week${if (weeks != 1L) "s" else ""}")
    }
    if (remainingDays > 0 || parts.isEmpty()) {
        parts.add("$remainingDays day${if (remainingDays != 1L) "s" else ""}")
    }
    
    return parts.joinToString(", ")
}

