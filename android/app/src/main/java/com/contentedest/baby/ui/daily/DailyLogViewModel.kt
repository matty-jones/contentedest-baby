package com.contentedest.baby.ui.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentedest.baby.data.local.EventEntity
import com.contentedest.baby.data.local.EventType
import com.contentedest.baby.data.repo.EventRepository
import com.contentedest.baby.domain.TimeRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DailyLogViewModel @Inject constructor(
    private val repo: EventRepository,
) : ViewModel() {
    private val _events = MutableStateFlow<List<EventEntity>>(emptyList())
    val events: StateFlow<List<EventEntity>> = _events

    fun load(date: LocalDate) {
        viewModelScope.launch {
            val range = com.contentedest.baby.domain.TimeRules.dayRangeEpochSeconds(date)
            _events.value = repo.eventsForDay(range.first, range.last)
        }
    }
}


