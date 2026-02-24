package com.wearbubbles

import android.app.Application
import com.wearbubbles.worker.MessageSyncWorker

class WearBubblesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        MessageSyncWorker.schedule(this)
    }
}
