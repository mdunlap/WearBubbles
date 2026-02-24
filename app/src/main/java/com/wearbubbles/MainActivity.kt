package com.wearbubbles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.ui.WearApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settingsDataStore = remember { SettingsDataStore(this@MainActivity) }
            var hasCredentials by remember { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(Unit) {
                hasCredentials = settingsDataStore.hasCredentials()
            }

            // Only render once we know credential state (avoids blocking main thread)
            hasCredentials?.let { creds ->
                WearApp(hasCredentials = creds)
            }
        }
    }
}
