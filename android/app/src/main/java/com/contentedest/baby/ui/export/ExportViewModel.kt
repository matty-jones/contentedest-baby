package com.contentedest.baby.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentedest.baby.data.repo.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class ExportState {
    object Idle : ExportState()
    object Loading : ExportState()
    data class Success(val data: String, val format: ExportFormat) : ExportState()
    data class Error(val message: String) : ExportState()
}

enum class ExportFormat { CSV, JSON }

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    fun exportData(format: ExportFormat) {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val data = when (format) {
                    ExportFormat.CSV -> eventRepository.exportToCsv()
                    ExportFormat.JSON -> eventRepository.exportToJson()
                }
                _exportState.value = ExportState.Success(data, format)
            } catch (e: Exception) {
                Timber.e(e, "Export failed")
                _exportState.value = ExportState.Error("Export failed: ${e.message}")
            }
        }
    }

    fun reset() {
        _exportState.value = ExportState.Idle
    }
}
