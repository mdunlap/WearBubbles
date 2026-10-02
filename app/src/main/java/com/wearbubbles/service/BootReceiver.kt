package com.wearbubbles.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the message listener after a reboot or app update; it stops itself if signed out. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> MessageListenerService.start(context)
        }
    }
}
