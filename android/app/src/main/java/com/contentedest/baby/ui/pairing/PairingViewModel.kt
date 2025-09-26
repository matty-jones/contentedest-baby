package com.contentedest.baby.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentedest.baby.net.ApiService
import com.contentedest.baby.net.PairRequest
import com.contentedest.baby.net.TokenStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val api: ApiService,
    private val tokenStorage: TokenStorage
) : ViewModel() {
    private val _paired = MutableStateFlow(false)
    val paired: StateFlow<Boolean> = _paired

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun pair(pairingCode: String, deviceId: String, name: String?) {
        viewModelScope.launch {
            try {
                _error.value = null // Clear previous errors
                val resp = api.pair(PairRequest(pairingCode, deviceId, name))
                tokenStorage.saveToken(resp.token)
                _paired.value = true
            } catch (e: Exception) {
                _error.value = "Pairing failed: ${e.message ?: "Unknown error"}"
                // Log the full error for debugging
                println("Pairing error: $e")
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
