package com.wearbubbles.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_URL] ?: ""
    }

    val password: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PASSWORD] ?: ""
    }

    suspend fun saveCredentials(serverUrl: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_URL] = serverUrl
            prefs[PASSWORD] = password
        }
    }

    suspend fun getServerUrl(): String = serverUrl.first()

    suspend fun getPassword(): String = password.first()

    suspend fun hasCredentials(): Boolean {
        val url = getServerUrl()
        val pwd = getPassword()
        return url.isNotBlank() && pwd.isNotBlank()
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
