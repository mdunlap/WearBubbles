package com.wearbubbles.socket

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.wearbubbles.api.dto.MessageDto
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray

sealed class SocketEvent {
    data class NewMessage(val message: MessageDto) : SocketEvent()
    data class UpdatedMessage(val message: MessageDto) : SocketEvent()
    data class TypingIndicator(val chatGuid: String, val isTyping: Boolean) : SocketEvent()
    data object Connected : SocketEvent()
    data object Disconnected : SocketEvent()
}

class SocketManager {

    companion object {
        private const val TAG = "SocketManager"
    }

    private var socket: Socket? = null
    private val gson = Gson()

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    var isConnected: Boolean = false
        private set

    fun connect(serverUrl: String, password: String) {
        disconnect()

        try {
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = 10
                reconnectionDelay = 2000
                reconnectionDelayMax = 30000
                query = "password=$password"
                transports = arrayOf("polling", "websocket")
            }

            val normalizedUrl = serverUrl.trimEnd('/')
            socket = IO.socket(normalizedUrl, opts).apply {
                on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "Socket connected")
                    isConnected = true
                    _events.tryEmit(SocketEvent.Connected)
                }

                on(Socket.EVENT_DISCONNECT) {
                    Log.d(TAG, "Socket disconnected")
                    isConnected = false
                    _events.tryEmit(SocketEvent.Disconnected)
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    Log.e(TAG, "Socket connection error: ${args.firstOrNull()}")
                    isConnected = false
                }

                on("new-message") { args ->
                    handleNewMessage(args)
                }

                on("updated-message") { args ->
                    handleUpdatedMessage(args)
                }

                on("typing-indicator") { args ->
                    handleTypingIndicator(args)
                }

                connect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect socket", e)
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        isConnected = false
    }

    private fun handleNewMessage(args: Array<Any>) {
        try {
            val json = args.firstOrNull()?.toString() ?: return
            val messageDto = gson.fromJson(json, MessageDto::class.java)
            _events.tryEmit(SocketEvent.NewMessage(messageDto))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing new-message", e)
        }
    }

    private fun handleUpdatedMessage(args: Array<Any>) {
        try {
            val json = args.firstOrNull()?.toString() ?: return
            val messageDto = gson.fromJson(json, MessageDto::class.java)
            _events.tryEmit(SocketEvent.UpdatedMessage(messageDto))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing updated-message", e)
        }
    }

    private fun handleTypingIndicator(args: Array<Any>) {
        try {
            val json = args.firstOrNull()?.toString() ?: return
            val obj = gson.fromJson(json, JsonObject::class.java)
            val chatGuid = obj.get("guid")?.asString ?: return
            val display = obj.get("display")?.asBoolean ?: false
            _events.tryEmit(SocketEvent.TypingIndicator(chatGuid, display))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing typing-indicator", e)
        }
    }
}
