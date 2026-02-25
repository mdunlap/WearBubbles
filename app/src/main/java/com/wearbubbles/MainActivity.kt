package com.wearbubbles

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.service.MessageListenerService
import com.wearbubbles.ui.WearApp

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op — worker will post if granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Start foreground service to keep socket alive for notifications
        MessageListenerService.start(this)

        val openChatGuid = intent?.getStringExtra("chatGuid")

        setContent {
            val settingsDataStore = remember { SettingsDataStore(this@MainActivity) }
            var hasCredentials by remember { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(Unit) {
                hasCredentials = settingsDataStore.hasCredentials()
            }

            // Only render once we know credential state (avoids blocking main thread)
            hasCredentials?.let { creds ->
                WearApp(hasCredentials = creds, openChatGuid = openChatGuid)
            }
        }
    }
}
