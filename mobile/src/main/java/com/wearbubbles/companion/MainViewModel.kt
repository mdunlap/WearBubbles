package com.wearbubbles.companion

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class WatchStatus(
    val serverConnected: Boolean = false,
    val serverUrl: String = "",
    val totalConversations: Int = 0,
    val unreadConversations: Int = 0,
    val lastMessageTime: Long? = null,
)

data class UiState(
    val url: String = "",
    val password: String = "",
    val watchNodeName: String? = null,
    val watchNodeId: String? = null,
    val sendStatus: SendStatus = SendStatus.Idle,
    val isSynced: Boolean = false,
    val watchStatus: WatchStatus? = null,
)

enum class SendStatus {
    Idle, Sending, Sent, AckOk, AckError, NoWatch, Resetting, ResetOk
}

class MainViewModel(application: Application) : AndroidViewModel(application),
    MessageClient.OnMessageReceivedListener {

    private val context: Context get() = getApplication()
    private val prefs = context.getSharedPreferences("companion", Context.MODE_PRIVATE)
    private val messageClient = Wearable.getMessageClient(context)

    private val _state = MutableStateFlow(
        UiState(
            url = prefs.getString("url", "") ?: "",
            password = prefs.getString("password", "") ?: "",
            isSynced = prefs.getBoolean("synced", false),
        )
    )
    val state: StateFlow<UiState> = _state

    init {
        messageClient.addListener(this)
        refreshWatchConnection()
        requestStatus()
    }

    override fun onCleared() {
        messageClient.removeListener(this)
    }

    fun refreshWatchConnection() {
        viewModelScope.launch {
            try {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                val watch = nodes.firstOrNull()
                _state.value = _state.value.copy(
                    watchNodeName = watch?.displayName,
                    watchNodeId = watch?.id,
                )
                if (watch != null) requestStatus()
            } catch (_: Exception) {
                _state.value = _state.value.copy(watchNodeName = null, watchNodeId = null)
            }
        }
    }

    fun setUrl(url: String) {
        _state.value = _state.value.copy(url = url, sendStatus = SendStatus.Idle)
        prefs.edit().putString("url", url).apply()
    }

    fun setPassword(password: String) {
        _state.value = _state.value.copy(password = password, sendStatus = SendStatus.Idle)
        prefs.edit().putString("password", password).apply()
    }

    fun sendToWatch() {
        val current = _state.value
        val nodeId = current.watchNodeId
        if (nodeId == null) {
            _state.value = current.copy(sendStatus = SendStatus.NoWatch)
            return
        }
        _state.value = current.copy(sendStatus = SendStatus.Sending)

        viewModelScope.launch {
            try {
                val payload = Gson().toJson(mapOf("url" to current.url, "password" to current.password))
                messageClient.sendMessage(nodeId, "/setup-credentials", payload.toByteArray()).await()
                _state.value = _state.value.copy(sendStatus = SendStatus.Sent)
            } catch (_: Exception) {
                _state.value = _state.value.copy(sendStatus = SendStatus.AckError)
            }
        }
    }

    fun resetWatch() {
        val current = _state.value
        val nodeId = current.watchNodeId
        if (nodeId == null) {
            _state.value = current.copy(sendStatus = SendStatus.NoWatch)
            return
        }
        _state.value = current.copy(sendStatus = SendStatus.Resetting)

        viewModelScope.launch {
            try {
                messageClient.sendMessage(nodeId, "/reset-watch", ByteArray(0)).await()
                _state.value = _state.value.copy(sendStatus = SendStatus.ResetOk, isSynced = false)
                prefs.edit().putBoolean("synced", false).apply()
            } catch (_: Exception) {
                _state.value = _state.value.copy(sendStatus = SendStatus.AckError)
            }
        }
    }

    fun requestStatus() {
        val nodeId = _state.value.watchNodeId ?: return
        viewModelScope.launch {
            try {
                messageClient.sendMessage(nodeId, "/request-status", ByteArray(0)).await()
            } catch (_: Exception) {
                // Watch may not be reachable
            }
        }
    }

    fun clearLocalData() {
        prefs.edit().clear().apply()
        _state.value = UiState(
            watchNodeName = _state.value.watchNodeName,
            watchNodeId = _state.value.watchNodeId,
        )
    }

    override fun onMessageReceived(event: MessageEvent) {
        // Must update state on main thread for Compose safety
        viewModelScope.launch {
            when (event.path) {
                "/setup-credentials-ack" -> {
                    val result = String(event.data)
                    val ok = result == "ok"
                    _state.value = _state.value.copy(
                        sendStatus = if (ok) SendStatus.AckOk else SendStatus.AckError,
                        isSynced = ok,
                    )
                    if (ok) {
                        prefs.edit().putBoolean("synced", true).apply()
                        requestStatus()
                    }
                }
                "/reset-ack" -> {
                    _state.value = _state.value.copy(sendStatus = SendStatus.ResetOk, isSynced = false, watchStatus = null)
                    prefs.edit().putBoolean("synced", false).apply()
                }
                "/watch-status" -> {
                    try {
                        val json = String(event.data)
                        val map = Gson().fromJson(json, Map::class.java) as Map<*, *>
                        _state.value = _state.value.copy(
                            watchStatus = WatchStatus(
                                serverConnected = map["serverConnected"] as? Boolean ?: false,
                                serverUrl = map["serverUrl"] as? String ?: "",
                                totalConversations = (map["totalConversations"] as? Double)?.toInt() ?: 0,
                                unreadConversations = (map["unreadConversations"] as? Double)?.toInt() ?: 0,
                                lastMessageTime = (map["lastMessageTime"] as? Double)?.toLong(),
                            )
                        )
                    } catch (_: Exception) { }
                }
            }
        }
    }
}
