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
import okhttp3.OkHttpClient
import java.net.URI
import java.net.URLEncoder

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
    // Credentials the current socket was opened with (it may still be handshaking)
    private var socketUrl: String? = null
    private var socketPassword: String? = null
    private val gson = Gson()

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    @Volatile
    var isConnected: Boolean = false
        private set

    /**
     * Connects unless a socket for these credentials already exists. Checking for an
     * existing socket rather than [isConnected] avoids tearing down a connection that
     * is still handshaking when several callers start up at once.
     */
    @Synchronized
    fun ensureConnected(serverUrl: String, password: String, okHttpClient: OkHttpClient? = null) {
        if (socket != null && socketUrl == serverUrl.trimEnd('/') && socketPassword == password) return
        connect(serverUrl, password, okHttpClient)
    }

    @Synchronized
    fun connect(serverUrl: String, password: String, okHttpClient: OkHttpClient? = null) {
        disconnect()

        try {
            val normalizedUrl = serverUrl.trimEnd('/')
            // Socket.IO treats a URL path as a namespace, so a server behind a path prefix
            // (https://host/bluebubbles) is reached via the origin plus a custom engine path
            val uri = URI(normalizedUrl)
            val basePath = uri.rawPath.orEmpty().trimEnd('/')
            val origin = URI(uri.scheme, null, uri.host, uri.port, null, null, null).toString()

            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 2000
                reconnectionDelayMax = 30000
                query = "password=${URLEncoder.encode(password, "UTF-8")}"
                transports = arrayOf("websocket")
                path = "$basePath/socket.io"
                if (okHttpClient != null) {
                    callFactory = okHttpClient
                    webSocketFactory = okHttpClient
                }
            }

            socketUrl = normalizedUrl
            socketPassword = password
            socket = IO.socket(origin, opts).apply {
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
                    val err = args.firstOrNull()
                    val cause = (err as? Throwable)?.cause
                    Log.e(TAG, "Socket connection error: $err cause: $cause", err as? Throwable)
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

    @Synchronized
    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        socketUrl = null
        socketPassword = null
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
