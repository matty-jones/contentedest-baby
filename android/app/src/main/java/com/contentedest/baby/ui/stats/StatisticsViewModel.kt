package com.contentedest.baby.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentedest.baby.data.repo.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
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

            // TODO: Implement actual statistics calculation
            // For now, show placeholder stats
            val stats = listOf(
                StatItem("Total Events", "0", "Last ${_currentRange.value.name.lowercase().replace('_', ' ')}"),
                StatItem("Sleep Sessions", "0", "Average duration: 0h 0m"),
                StatItem("Feed Sessions", "0", "Total feeds"),
                StatItem("Nappy Changes", "0", "Total changes"),
                StatItem("Longest Sleep", "0h 0m", "Best rest period")
            )

            _stats.value = stats
        }
    }
}
