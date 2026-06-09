package com.wearbubbles.companion

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val url: String)

private data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("html_url") val htmlUrl: String
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val RELEASES_URL = "https://api.github.com/repos/mdunlap/WearBubbles/releases/latest"

    private val gson = Gson()

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            try {
                if (connection.responseCode != 200) return@withContext null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val release = gson.fromJson(body, GitHubRelease::class.java)
                val version = release.tagName.removePrefix("v")
                if (isNewer(version)) UpdateInfo(version, release.htmlUrl) else null
            } finally {
                connection.disconnect()
            }
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
