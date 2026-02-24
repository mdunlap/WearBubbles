package com.wearbubbles.ui.conversations

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wearbubbles.WearBubblesApp
import com.wearbubbles.api.ApiClient
import com.wearbubbles.data.ChatRepository
import com.wearbubbles.data.ContactRepository
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.db.AppDatabase
import com.wearbubbles.db.entities.ChatEntity
import com.wearbubbles.socket.SocketManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Immutable
data class ChatUiItem(
    val guid: String,
    val displayName: String,
    val lastMessage: String,
    val timestamp: Long?,
    val isFromMe: Boolean,
    val hasUnread: Boolean
)

@Immutable
data class ConversationListUiState(
    val chats: List<ChatUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)

class ConversationListViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val db = AppDatabase.getInstance(application)
    private val socketManager = (application as WearBubblesApp).socketManager

    private lateinit var chatRepository: ChatRepository
    private lateinit var contactRepository: ContactRepository

    // Cached credentials for sync access
    private var cachedPassword: String? = null

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
                cachedPassword = password
                val api = ApiClient.getInstance(serverUrl)

                contactRepository = ContactRepository(api, password, db.contactDao())
                chatRepository = ChatRepository(
                    context = getApplication(),
                    api = api,
                    password = password,
                    chatDao = db.chatDao(),
                    socketManager = socketManager,
                    contactRepository = contactRepository,
                    scope = viewModelScope
                )

                // 1. Load contacts from Room cache (instant — covers repeat launches)
                contactRepository.loadFromCache()

                // 2. Load contacts from API (awaited — ensures names are ready before showing chats)
                contactRepository.loadContacts()

                // 3. Connect socket
                socketManager.connect(serverUrl, password)

                // 4. NOW observe chats — contacts are fully loaded, names will resolve correctly
                launch {
                    chatRepository.chats.collect { entities ->
                        _uiState.value = _uiState.value.copy(
                            chats = entities.map { it.toUiItem() },
                            isLoading = false
                        )
                    }
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
                _uiState.value = _uiState.value.copy(hasMore = chatRepository.hasMoreChats())
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            if (!::chatRepository.isInitialized) return@launch
            if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return@launch
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            chatRepository.loadMore()
            _uiState.value = _uiState.value.copy(
                isLoadingMore = false,
                hasMore = chatRepository.hasMoreChats()
            )
        }
    }

    fun deleteChat(guid: String) {
        viewModelScope.launch {
            db.chatDao().deleteChat(guid)
        }
    }

    fun getSocketManager(): SocketManager = socketManager

    fun getPassword(): String? = cachedPassword

    // Non-suspend: uses in-memory contact cache — zero DB round-trips
    private fun ChatEntity.toUiItem(): ChatUiItem {
        return ChatUiItem(
            guid = guid,
            displayName = if (::chatRepository.isInitialized) {
                chatRepository.getDisplayNameSync(this)
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
