package com.wearbubbles.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val PASSWORD = stringPreferencesKey("password")
        private val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        private val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        private val LATEST_VERSION = stringPreferencesKey("latest_version")
        private val LATEST_VERSION_URL = stringPreferencesKey("latest_version_url")
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_URL] ?: ""
    }

    val password: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PASSWORD] ?: ""
    }

    val hapticEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HAPTIC_ENABLED] ?: true
    }

    suspend fun saveCredentials(serverUrl: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_URL] = serverUrl
            prefs[PASSWORD] = password
        }
    }

    suspend fun getServerUrl(): String = serverUrl.first()

    suspend fun getPassword(): String = password.first()

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HAPTIC_ENABLED] = enabled
        }
    }

    suspend fun getHapticEnabled(): Boolean = hapticEnabled.first()

    suspend fun hasCredentials(): Boolean {
        val url = getServerUrl()
        val pwd = getPassword()
        return url.isNotBlank() && pwd.isNotBlank()
    }

    suspend fun getLastUpdateCheck(): Long = context.dataStore.data.first()[LAST_UPDATE_CHECK] ?: 0L

    suspend fun getLatestVersion(): String? = context.dataStore.data.first()[LATEST_VERSION]

    suspend fun getLatestVersionUrl(): String? = context.dataStore.data.first()[LATEST_VERSION_URL]

    suspend fun saveUpdateCheck(version: String, url: String) {
        context.dataStore.edit { prefs ->
            prefs[LAST_UPDATE_CHECK] = System.currentTimeMillis()
            prefs[LATEST_VERSION] = version
            prefs[LATEST_VERSION_URL] = url
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
