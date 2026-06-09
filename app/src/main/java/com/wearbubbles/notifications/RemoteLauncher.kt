package com.wearbubbles.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.wear.remote.interactions.RemoteActivityHelper
import java.util.concurrent.Executors

/** Opens URLs in the browser of the paired phone via the Wearable Data Layer. */
object RemoteLauncher {

    private const val TAG = "RemoteLauncher"

    fun openUrlOnPhone(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse(url))

            RemoteActivityHelper(context, Executors.newSingleThreadExecutor())
                .startRemoteActivity(intent)

            Toast.makeText(context, "Check your phone", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL on phone", e)
            Toast.makeText(context, "Couldn't reach phone", Toast.LENGTH_SHORT).show()
        }
    }
}
