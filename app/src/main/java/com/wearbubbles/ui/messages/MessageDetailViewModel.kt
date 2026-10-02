package com.wearbubbles.ui.messages

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wearbubbles.api.ApiClient
import com.wearbubbles.data.MessageRepository
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.data.attachmentLabel
import com.wearbubbles.db.AppDatabase
import com.wearbubbles.db.entities.MessageEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Immutable
data class MessageUiItem(
    val guid: String,
    val text: String,
    val isFromMe: Boolean,
    val dateCreated: Long,
    val isTemporary: Boolean = false,
    val attachmentGuid: String? = null,
    val attachmentMimeType: String? = null,
    val hasLoveReaction: Boolean = false,
    val sendFailed: Boolean = false
)

@Immutable
data class MessageDetailUiState(
    val messages: List<MessageUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
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

    fun initialize(chatGuid: String) {
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

                // Live socket messages reach Room via the app-wide IncomingMessageHandler
                messageRepository = MessageRepository(
                    api = api,
                    password = password,
                    messageDao = db.messageDao()
                )

                // Start observing Room IMMEDIATELY — shows cached messages instantly
                launch {
                    messageRepository.getMessages(chatGuid).collect { entities ->
                        val lovedGuids = lovedMessageGuids(entities)

                        // Filter out reaction messages, map to UI items with love info
                        val messages = entities
                            .filter { it.associatedMessageGuid == null }
                            .map { it.toUiItem(hasLoveReaction = it.guid in lovedGuids) }

                        _uiState.value = _uiState.value.copy(
                            messages = messages,
                            isLoading = false
                        )
                    }
                }

                // Refresh from API in background — Room flow auto-updates when new data arrives
                launch {
                    Log.d("MessageDetailVM", "Fetching messages for $chatGuid")
                    messageRepository.refreshMessages(chatGuid)
                    _uiState.value = _uiState.value.copy(
                        hasMoreMessages = messageRepository.hasMoreMessages()
                    )
                }

                // Mark chat as read in background
                launch {
                    try {
                        db.chatDao().markRead(chatGuid)
                        api.markChatRead(chatGuid, password)
                    } catch (_: Exception) {}
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

    fun loadMore() {
        viewModelScope.launch {
            if (!::messageRepository.isInitialized) return@launch
            if (_uiState.value.isLoadingMore || !_uiState.value.hasMoreMessages) return@launch
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            messageRepository.loadMoreMessages(chatGuid)
            _uiState.value = _uiState.value.copy(
                isLoadingMore = false,
                hasMoreMessages = messageRepository.hasMoreMessages()
            )
        }
    }

    fun reactToMessage(messageGuid: String) {
        viewModelScope.launch {
            if (::messageRepository.isInitialized) {
                messageRepository.reactToMessage(chatGuid, messageGuid)
            }
        }
    }

    fun retryMessage(guid: String) {
        viewModelScope.launch {
            val entity = db.messageDao().getMessageByGuid(guid) ?: return@launch
            _uiState.value = _uiState.value.copy(error = null)
            if (::messageRepository.isInitialized) {
                val success = messageRepository.retrySendMessage(entity)
                if (!success) {
                    _uiState.value = _uiState.value.copy(error = "Failed to send")
                }
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

    private fun MessageEntity.toUiItem(hasLoveReaction: Boolean = false): MessageUiItem {
        return MessageUiItem(
            guid = guid,
            // Non-image attachments have no inline preview, so label them instead of hiding the bubble
            text = text?.ifBlank { null }
                ?: attachmentLabel(attachmentMimeType)?.takeIf { attachmentGuid == null }?.let { "📎 $it" }
                ?: "",
            isFromMe = isFromMe,
            dateCreated = dateCreated,
            isTemporary = isTemporary,
            attachmentGuid = attachmentGuid,
            attachmentMimeType = attachmentMimeType,
            hasLoveReaction = hasLoveReaction,
            sendFailed = sendFailed
        )
    }
}
