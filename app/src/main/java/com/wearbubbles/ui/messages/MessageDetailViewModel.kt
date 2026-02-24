package com.wearbubbles.ui.messages

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wearbubbles.api.ApiClient
import com.wearbubbles.data.MessageRepository
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.db.AppDatabase
import com.wearbubbles.db.entities.MessageEntity
import com.wearbubbles.socket.SocketManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MessageUiItem(
    val guid: String,
    val text: String,
    val isFromMe: Boolean,
    val dateCreated: Long,
    val isTemporary: Boolean = false,
    val attachmentGuid: String? = null,
    val attachmentMimeType: String? = null
)

data class MessageDetailUiState(
    val messages: List<MessageUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val error: String? = null,
    val serverUrl: String = "",
    val password: String = ""
)

class MessageDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val db = AppDatabase.getInstance(application)

    private lateinit var messageRepository: MessageRepository

    private val _uiState = MutableStateFlow(MessageDetailUiState())
    val uiState: StateFlow<MessageDetailUiState> = _uiState.asStateFlow()

    private var chatGuid: String = ""
    private var initialized = false

    fun initialize(chatGuid: String, socketManager: SocketManager?) {
        if (initialized && this.chatGuid == chatGuid) return
        this.chatGuid = chatGuid
        initialized = true

        viewModelScope.launch {
            try {
                val serverUrl = settingsDataStore.getServerUrl()
                val password = settingsDataStore.getPassword()
                val api = ApiClient.getInstance(serverUrl)

                _uiState.value = _uiState.value.copy(
                    serverUrl = serverUrl,
                    password = password
                )

                messageRepository = MessageRepository(
                    api = api,
                    password = password,
                    messageDao = db.messageDao(),
                    socketManager = socketManager ?: SocketManager(),
                    scope = viewModelScope
                )

                // Mark chat as read
                try {
                    db.chatDao().markRead(chatGuid)
                    api.markChatRead(chatGuid, password)
                } catch (_: Exception) {}

                // Fetch messages from API
                Log.d("MessageDetailVM", "Fetching messages for $chatGuid")
                messageRepository.refreshMessages(chatGuid)

                // Observe from Room
                messageRepository.getMessages(chatGuid).collect { entities ->
                    _uiState.value = _uiState.value.copy(
                        messages = entities.map { it.toUiItem() },
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("MessageDetailVM", "Error initializing", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            if (::messageRepository.isInitialized) {
                val success = messageRepository.sendMessage(chatGuid, text)
                if (!success) {
                    _uiState.value = _uiState.value.copy(error = "Failed to send")
                }
            }
            _uiState.value = _uiState.value.copy(isSending = false)
        }
    }

    private fun MessageEntity.toUiItem(): MessageUiItem {
        return MessageUiItem(
            guid = guid,
            text = text ?: "",
            isFromMe = isFromMe,
            dateCreated = dateCreated,
            isTemporary = isTemporary,
            attachmentGuid = attachmentGuid,
            attachmentMimeType = attachmentMimeType
        )
    }
}
