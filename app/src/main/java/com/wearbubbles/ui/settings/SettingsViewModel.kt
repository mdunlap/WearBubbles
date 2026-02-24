package com.wearbubbles.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wearbubbles.WearBubblesApp
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.db.AppDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val db = AppDatabase.getInstance(application)
    private val socketManager = (application as WearBubblesApp).socketManager

    val hapticEnabled: StateFlow<Boolean> = settingsDataStore.hapticEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val serverUrl: StateFlow<String> = settingsDataStore.serverUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val isConnected: Boolean
        get() = socketManager.isConnected

    fun toggleHaptic() {
        viewModelScope.launch {
            settingsDataStore.setHapticEnabled(!hapticEnabled.value)
        }
    }

    fun resetWatch(onComplete: () -> Unit) {
        viewModelScope.launch {
            settingsDataStore.clear()
            db.clearAllTables()
            socketManager.disconnect()
            onComplete()
        }
    }
}
