package com.wearbubbles.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wearbubbles.WearBubblesApp
import com.wearbubbles.BuildConfig
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.data.UpdateChecker
import com.wearbubbles.data.UpdateInfo
import com.wearbubbles.notifications.RemoteLauncher
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

    val currentVersion: String = BuildConfig.VERSION_NAME

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    init {
        viewModelScope.launch {
            _updateInfo.value = UpdateChecker.check(getApplication())
        }
    }

    fun openReleaseOnPhone() {
        val info = _updateInfo.value ?: return
        RemoteLauncher.openUrlOnPhone(getApplication(), info.url)
    }

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
