package com.wearbubbles

import android.app.Application
import com.wearbubbles.notifications.NotificationHelper
import com.wearbubbles.socket.SocketManager
import com.wearbubbles.worker.MessageSyncWorker

class WearBubblesApp : Application() {

    val socketManager = SocketManager()

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        MessageSyncWorker.schedule(this)
    }
}
