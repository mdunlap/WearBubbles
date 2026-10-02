package com.wearbubbles.data

import com.wearbubbles.api.ApiClient
import com.wearbubbles.api.BlueBubblesApi
import java.net.URLEncoder

/** The configured server, read fresh on each use so credential changes take effect immediately. */
data class ServerSession(
    val serverUrl: String,
    val password: String,
    val api: BlueBubblesApi
)

suspend fun SettingsDataStore.currentSession(): ServerSession? {
    val serverUrl = getServerUrl()
    val password = getPassword()
    if (serverUrl.isBlank() || password.isBlank()) return null
    return ServerSession(serverUrl, password, ApiClient.getInstance(serverUrl))
}

/** Download URL for an attachment; the password is encoded so characters like & + # % survive. */
fun attachmentUrl(serverUrl: String, attachmentGuid: String, password: String): String =
    "${serverUrl.trimEnd('/')}/api/v1/attachment/$attachmentGuid/download" +
        "?password=${URLEncoder.encode(password, "UTF-8")}"
