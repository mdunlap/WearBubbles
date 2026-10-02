package com.wearbubbles.ui.conversations

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.wearbubbles.WearBubblesApp
import com.wearbubbles.data.attachmentLabel
import com.wearbubbles.data.attachmentUrl
import com.wearbubbles.data.currentSession
import com.wearbubbles.db.entities.ChatEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val error: String? = null
)

class ConversationListViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WearBubblesApp
    private val chatRepository = app.chatRepository
    private val contactRepository = app.contactRepository

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    // Session the screen was loaded for; a sign-out/sign-in since (even with the same
    // credentials) reloads contacts and chats instead of reusing the old server and password
    private var sessionKey: String? = null
    private var sessionJob: Job? = null

    fun initialize() {
        viewModelScope.launch {
            val session = app.settings.currentSession() ?: return@launch
            val key = "${app.sessionManager.generation}\n${session.serverUrl}\n${session.password}"
            if (key == sessionKey) return@launch
            sessionKey = key

            sessionJob?.cancel()
            sessionJob = launch {
                try {
                    // 1. Load contacts from Room cache (instant — covers repeat launches)
                    contactRepository.loadFromCache()

                    // 2. Load contacts from API (awaited — ensures names are ready before showing chats)
                    contactRepository.loadContacts(session.api, session.password)

                    // 3. Ensure socket is connected and kept alive (no-op if the service already did)
                    app.sessionManager.start()

                    // 4. NOW observe chats — contacts are fully loaded, names will resolve correctly
                    chatRepository.chats.collect { entities ->
                        _uiState.value = _uiState.value.copy(
                            chats = entities.map { it.toUiItem() },
                            isLoading = false,
                            error = null
                        )
                        prefetchAttachmentImages(entities, session.serverUrl, session.password)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            chatRepository.refreshChats()
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /** Hides the chat until a new message arrives in it (deleting would just re-sync it). */
    fun deleteChat(guid: String) {
        viewModelScope.launch {
            chatRepository.hideChat(guid)
        }
    }

    private fun prefetchAttachmentImages(chats: List<ChatEntity>, serverUrl: String, password: String) {
        val context = getApplication<Application>()
        val imageLoader = Coil.imageLoader(context)
        chats.take(10)
            .forEach { chat ->
                val attachmentGuid = chat.lastMessageAttachmentGuid ?: return@forEach
                val request = ImageRequest.Builder(context)
                    .data(attachmentUrl(serverUrl, attachmentGuid, password))
                    .size(250, 250)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                imageLoader.enqueue(request)
            }
    }

    // Non-suspend: uses in-memory contact cache — zero DB round-trips
    private fun ChatEntity.toUiItem(): ChatUiItem {
        return ChatUiItem(
            guid = guid,
            displayName = chatRepository.getDisplayNameSync(this),
            lastMessage = lastMessageText?.ifBlank { null }
                ?: attachmentLabel(lastMessageAttachmentMimeType)
                ?: "",
            timestamp = lastMessageDate,
            isFromMe = lastMessageIsFromMe ?: false,
            hasUnread = hasUnreadMessage
        )
    }

    // Socket lifecycle is managed by SessionManager / MessageListenerService — don't disconnect here
}
