package com.wearbubbles.ui.conversations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wearbubbles.api.ApiClient
import com.wearbubbles.data.ChatRepository
import com.wearbubbles.data.ContactRepository
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.db.AppDatabase
import com.wearbubbles.db.entities.ChatEntity
import com.wearbubbles.socket.SocketManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiItem(
    val guid: String,
    val displayName: String,
    val lastMessage: String,
    val timestamp: Long?,
    val isFromMe: Boolean,
    val hasUnread: Boolean
)

data class ConversationListUiState(
    val chats: List<ChatUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class ConversationListViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val db = AppDatabase.getInstance(application)
    private val socketManager = SocketManager()

    private lateinit var chatRepository: ChatRepository
    private lateinit var contactRepository: ContactRepository

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true

        viewModelScope.launch {
            try {
                val serverUrl = settingsDataStore.getServerUrl()
                val password = settingsDataStore.getPassword()
                val api = ApiClient.getInstance(serverUrl)

                contactRepository = ContactRepository(api, password)
                chatRepository = ChatRepository(
                    api = api,
                    password = password,
                    chatDao = db.chatDao(),
                    socketManager = socketManager,
                    contactRepository = contactRepository,
                    scope = viewModelScope
                )

                // Load contacts and connect socket
                contactRepository.loadContacts()
                socketManager.connect(serverUrl, password)

                // Observe chats from Room
                chatRepository.chats.collect { entities ->
                    _uiState.value = _uiState.value.copy(
                        chats = entities.map { it.toUiItem() },
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            if (::chatRepository.isInitialized) {
                chatRepository.refreshChats()
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun getSocketManager(): SocketManager = socketManager
    fun getPassword(): String? = runCatching {
        kotlinx.coroutines.runBlocking { settingsDataStore.getPassword() }
    }.getOrNull()

    private fun ChatEntity.toUiItem(): ChatUiItem {
        return ChatUiItem(
            guid = guid,
            displayName = if (::chatRepository.isInitialized) {
                chatRepository.getDisplayName(this)
            } else {
                displayName ?: chatIdentifier ?: guid
            },
            lastMessage = lastMessageText ?: "",
            timestamp = lastMessageDate,
            isFromMe = lastMessageIsFromMe ?: false,
            hasUnread = hasUnreadMessage
        )
    }

    override fun onCleared() {
        super.onCleared()
        socketManager.disconnect()
    }
}
