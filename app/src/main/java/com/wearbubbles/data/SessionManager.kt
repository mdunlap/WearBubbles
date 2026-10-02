package com.wearbubbles.data

import com.wearbubbles.WearBubblesApp
import com.wearbubbles.api.ApiClient
import com.wearbubbles.notifications.NotificationHelper
import com.wearbubbles.service.MessageListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns credential changes so every running piece (socket, foreground service,
 * caches, notifications) follows them — whichever screen or the phone made the change.
 */
class SessionManager(private val app: WearBubblesApp) {

    /** Bumped on every sign-in/sign-out so screens know to reload even for identical credentials. */
    @Volatile
    var generation = 0
        private set

    /** Connects the shared socket for the saved credentials. Returns false when signed out. */
    suspend fun connectSocket(): Boolean {
        val session = app.settings.currentSession() ?: return false
        app.socketManager.ensureConnected(session.serverUrl, session.password, ApiClient.getHttpClient())
        return true
    }

    /** Connects and starts the foreground service that keeps the socket alive in the background. */
    suspend fun start() {
        if (connectSocket()) {
            MessageListenerService.start(app)
        }
    }

    suspend fun signIn(serverUrl: String, password: String) {
        // Switching servers: drop the old server's chats, messages and contacts
        val previousUrl = app.settings.getServerUrl()
        if (previousUrl.isNotBlank() && previousUrl.trimEnd('/') != serverUrl.trimEnd('/')) {
            clearCachedData()
        }
        app.settings.saveCredentials(serverUrl, password)
        generation++
        start()
    }

    suspend fun signOut() {
        MessageListenerService.stop(app)
        app.socketManager.disconnect()
        app.settings.clear()
        generation++
        clearCachedData()
    }

    private suspend fun clearCachedData() {
        app.contactRepository.clear()
        // Room refuses to clear tables on the main thread
        withContext(Dispatchers.IO) { app.database.clearAllTables() }
        NotificationHelper.cancelAll(app)
    }
}
