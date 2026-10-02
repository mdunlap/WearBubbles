package com.wearbubbles.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles update-notification taps: opens the release page on the paired phone. */
class OpenOnPhoneReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_URL = "url"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL) ?: return
        // Keep the receiver alive until the phone answers so the result toast still shows
        val pendingResult = goAsync()
        RemoteLauncher.openUrlOnPhone(context, url) { pendingResult.finish() }
    }
}
