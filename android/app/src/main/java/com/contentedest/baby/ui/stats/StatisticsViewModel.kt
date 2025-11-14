package com.contentedest.baby.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentedest.baby.data.repo.EventRepository
import com.contentedest.baby.domain.StatsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _stats = MutableStateFlow<List<StatItem>>(emptyList())
    val stats: StateFlow<List<StatItem>> = _stats

    private val _currentRange = MutableStateFlow(StatsRange.LAST_7_DAYS)
    val currentRange: StateFlow<StatsRange> = _currentRange

    init {
        loadStats()
    }

    fun setRange(range: StatsRange) {
        _currentRange.value = range
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            val endDate = LocalDate.now()
            val startDate = when (_currentRange.value) {
                StatsRange.LAST_7_DAYS -> endDate.minusDays(7)
                StatsRange.LAST_30_DAYS -> endDate.minusDays(30)
                StatsRange.CUSTOM -> endDate.minusDays(30) // Default to 30 days for custom
            }

            // Convert dates to epoch seconds
            val startEpoch = startDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
            val endEpoch = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()

            // Fetch events in the date range
            val events = eventRepository.eventsForDay(startEpoch, endEpoch)

            // Calculate statistics
            val sleepStats = StatsUseCases.computeSleepStats(events)
            val feedStats = StatsUseCases.computeFeedStats(events)
            val nappyStats = StatsUseCases.computeNappyStats(events)

            // Calculate total events
            val totalEvents = events.size

            // Calculate average sleep duration
            val sleepSessions = events.filter { it.type == com.contentedest.baby.data.local.EventType.sleep && it.start_ts != null && it.end_ts != null }
            val avgSleepDuration = if (sleepSessions.isNotEmpty()) {
                sleepStats.totalSeconds / sleepSessions.size
            } else {
                0L
            }

            // Format range text
            val rangeText = when (_currentRange.value) {
                StatsRange.LAST_7_DAYS -> "Last 7 days"
                StatsRange.LAST_30_DAYS -> "Last 30 days"
                StatsRange.CUSTOM -> "Custom range"
            }

            // Format durations
            fun formatDuration(seconds: Long): String {
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                return when {
                    hours > 0 -> "${hours}h ${minutes}m"
                    minutes > 0 -> "${minutes}m"
                    else -> "${seconds}s"
                }
            }

            val stats = listOf(
                StatItem("Total Events", totalEvents.toString(), rangeText),
                StatItem("Sleep Sessions", sleepSessions.size.toString(), "Average duration: ${formatDuration(avgSleepDuration)}"),
                StatItem("Feed Sessions", feedStats.feedCount.toString(), "Total feeds"),
                StatItem("Nappy Changes", nappyStats.count.toString(), "Total changes"),
                StatItem("Longest Sleep", formatDuration(sleepStats.longestStretchSeconds), "Best rest period")
            )

            _stats.value = stats
        }
    }
}
