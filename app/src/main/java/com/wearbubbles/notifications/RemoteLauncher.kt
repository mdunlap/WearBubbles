package com.wearbubbles.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper

/** Opens URLs in the browser of the paired phone via the Wearable Data Layer. */
object RemoteLauncher {

    private const val TAG = "RemoteLauncher"

    /** Toasts the real outcome once the phone responds, then calls [onDone]. */
    fun openUrlOnPhone(context: Context, url: String, onDone: () -> Unit = {}) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        try {
            val intent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse(url))

            val future = RemoteActivityHelper(context, mainExecutor).startRemoteActivity(intent)
            future.addListener({
                val opened = try {
                    future.get()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open URL on phone", e)
                    false
                }
                showResult(context, opened)
                onDone()
            }, mainExecutor)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL on phone", e)
            showResult(context, opened = false)
            onDone()
        }
    }

    private fun showResult(context: Context, opened: Boolean) {
        val text = if (opened) "Check your phone" else "Couldn't reach phone"
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
