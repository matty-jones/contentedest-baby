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

data class UndoAction(
    val type: UndoType,
    val eventId: String,
    val previousEvent: EventEntity? = null
)

enum class UndoType { DELETE, EDIT }

@HiltViewModel
class DailyLogViewModel @Inject constructor(
    private val repo: EventRepository,
) : ViewModel() {
    private val _events = MutableStateFlow<List<EventEntity>>(emptyList())
    val events: StateFlow<List<EventEntity>> = _events

    private val _undoStack = MutableStateFlow<List<UndoAction>>(emptyList())
    val undoStack: StateFlow<List<UndoAction>> = _undoStack

    private val _showUndoSnackbar = MutableStateFlow(false)
    val showUndoSnackbar: StateFlow<Boolean> = _showUndoSnackbar

    fun load(date: LocalDate) {
        viewModelScope.launch {
            val range = com.contentedest.baby.domain.TimeRules.dayRangeEpochSeconds(date)
            _events.value = repo.eventsForDay(range.first, range.last)
        }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch {
            val undoAction = UndoAction(UndoType.DELETE, event.event_id, event)
            _undoStack.value = _undoStack.value + undoAction
            _showUndoSnackbar.value = true

            // TODO: Implement soft delete in repository
            // For now, just remove from local state
            _events.value = _events.value.filter { it.event_id != event.event_id }
        }
    }

    fun undoLastAction() {
        val lastAction = _undoStack.value.lastOrNull()
        if (lastAction != null) {
            viewModelScope.launch {
                when (lastAction.type) {
                    UndoType.DELETE -> {
                        // Restore the deleted event
                        if (lastAction.previousEvent != null) {
                            _events.value = _events.value + lastAction.previousEvent
                        }
                    }
                    UndoType.EDIT -> {
                        // TODO: Restore previous version of edited event
                    }
                }
                _undoStack.value = _undoStack.value.dropLast(1)
                _showUndoSnackbar.value = false
            }
        }
    }

    fun dismissUndoSnackbar() {
        _showUndoSnackbar.value = false
    }
}


