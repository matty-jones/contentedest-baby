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

    fun pair(pairingCode: String, deviceId: String, name: String?) {
        viewModelScope.launch {
            val resp = api.pair(PairRequest(pairingCode, deviceId, name))
            tokenStorage.saveToken(resp.token)
            _paired.value = true
        }
    }
}
