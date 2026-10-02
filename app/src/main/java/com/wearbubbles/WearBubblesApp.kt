package com.wearbubbles

import android.app.Application
import com.wearbubbles.data.ChatRepository
import com.wearbubbles.data.ContactRepository
import com.wearbubbles.data.IncomingMessageHandler
import com.wearbubbles.data.SessionManager
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.db.AppDatabase
import com.wearbubbles.notifications.NotificationHelper
import com.wearbubbles.socket.SocketManager
import com.wearbubbles.worker.MessageSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WearBubblesApp : Application() {

    /** Process-lifetime scope for work that must outlive any screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val socketManager = SocketManager()
    val settings by lazy { SettingsDataStore(this) }
    val database by lazy { AppDatabase.getInstance(this) }
    val contactRepository by lazy { ContactRepository(database.contactDao()) }
    val chatRepository by lazy { ChatRepository(database.chatDao(), settings, contactRepository) }
    val sessionManager by lazy { SessionManager(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        MessageSyncWorker.schedule(this)
        IncomingMessageHandler(
            context = this,
            socketManager = socketManager,
            chatDao = database.chatDao(),
            messageDao = database.messageDao(),
            chatRepository = chatRepository,
            contactRepository = contactRepository
        ).start(appScope)
    }
}
