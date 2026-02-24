package com.wearbubbles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.ui.WearApp
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsDataStore = SettingsDataStore(this)
        val hasCredentials = runBlocking { settingsDataStore.hasCredentials() }

        setContent {
            WearApp(hasCredentials = hasCredentials)
        }
    }
}
