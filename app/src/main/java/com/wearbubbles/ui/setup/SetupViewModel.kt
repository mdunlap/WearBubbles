package com.wearbubbles.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wearbubbles.api.ApiClient
import com.wearbubbles.data.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SetupUiState(
    val serverUrl: String = "https://73b8-65-78-17-120.ngrok-free.app",
    val password: String = "7Pheasant!",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = false
)

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val url = settingsDataStore.getServerUrl()
            val pwd = settingsDataStore.getPassword()
            _uiState.value = _uiState.value.copy(
                serverUrl = url.ifBlank { _uiState.value.serverUrl },
                password = pwd.ifBlank { _uiState.value.password }
            )
        }
    }

    fun onServerUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, error = null)
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun connect() {
        val state = _uiState.value
        if (state.serverUrl.isBlank()) {
            _uiState.value = state.copy(error = "Enter server URL")
            return
        }
        if (state.password.isBlank()) {
            _uiState.value = state.copy(error = "Enter password")
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val api = ApiClient.getInstance(state.serverUrl)
                val response = api.ping(state.password)
                if (response.status == 200) {
                    settingsDataStore.saveCredentials(state.serverUrl, state.password)
                    _uiState.value = _uiState.value.copy(isLoading = false, isConnected = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Server error: ${response.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Connection failed"
                )
            }
        }
    }
}
