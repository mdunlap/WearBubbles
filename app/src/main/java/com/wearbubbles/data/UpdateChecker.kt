package com.wearbubbles.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.wearbubbles.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateInfo(val version: String, val url: String)

private data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("html_url") val htmlUrl: String
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val THROTTLE_MS = 6 * 60 * 60 * 1000L // 6 hours
    private const val RELEASES_URL = "https://api.github.com/repos/mdunlap/WearBubbles/releases/latest"

    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun check(context: Context): UpdateInfo? {
        val settings = SettingsDataStore(context)

        // Return cached result if checked recently
        val lastCheck = settings.getLastUpdateCheck()
        if (System.currentTimeMillis() - lastCheck < THROTTLE_MS) {
            val cached = settings.getLatestVersion()
            val cachedUrl = settings.getLatestVersionUrl()
            if (cached != null && cachedUrl != null && isNewer(cached)) {
                return UpdateInfo(cached, cachedUrl)
            }
            return null
        }

        return try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val release = gson.fromJson(body, GitHubRelease::class.java)

            val version = release.tagName.removePrefix("v")
            settings.saveUpdateCheck(version, release.htmlUrl)

            if (isNewer(version)) UpdateInfo(version, release.htmlUrl) else null
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            null
        }
    }

    private fun isNewer(remote: String): Boolean {
        val current = BuildConfig.VERSION_NAME.split(".").mapNotNull { it.toIntOrNull() }
        val latest = remote.split(".").mapNotNull { it.toIntOrNull() }
        val len = maxOf(current.size, latest.size)
        for (i in 0 until len) {
            val c: Int = if (i < current.size) current[i] else 0
            val l: Int = if (i < latest.size) latest[i] else 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
