package com.wearbubbles.ui.conversations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults.ItemType
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState

@Composable
fun ConversationListScreen(
    onChatClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ConversationListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Track which chat is pending delete confirmation
    var pendingDeleteGuid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.initialize()
        viewModel.refresh()
    }

    val columnState = rememberResponsiveColumnState(
        contentPadding = ScalingLazyColumnDefaults.padding(
            first = ItemType.Text,
            last = ItemType.Chip
        )
    )

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        columnState = columnState
    ) {
        item(key = "header") {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary
            )
        }

        if (uiState.isLoading && uiState.chats.isEmpty()) {
            item(key = "loading") {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        uiState.error?.let { error ->
            item(key = "error") {
                Text(
                    text = error,
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption3,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        if (uiState.chats.isEmpty() && !uiState.isLoading) {
            item(key = "empty") {
                Text(
                    text = "No conversations",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }

        items(
            count = uiState.chats.size,
            key = { uiState.chats[it].guid }
        ) { index ->
            val chat = uiState.chats[index]
            if (pendingDeleteGuid == chat.guid) {
                // Confirm delete chip
                Chip(
                    onClick = {
                        viewModel.deleteChat(chat.guid)
                        pendingDeleteGuid = null
                    },
                    label = {
                        Text("Remove ${chat.displayName}?", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    secondaryLabel = {
                        Text("Tap to confirm, swipe to cancel")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.chipColors(
                        backgroundColor = MaterialTheme.colors.error
                    )
                )
            } else {
                ChatItem(
                    chat = chat,
                    onClick = { onChatClick(chat.guid) },
                    onLongClick = { pendingDeleteGuid = chat.guid }
                )
            }
        }

        // Load more chip
        if (uiState.hasMore && uiState.chats.isNotEmpty()) {
            item(key = "load_more") {
                if (uiState.isLoadingMore) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Chip(
                        onClick = { viewModel.loadMore() },
                        label = {
                            Text(
                                text = "Load more",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }
        }

        item(key = "spacer") { Spacer(modifier = Modifier.height(4.dp)) }

        item(key = "settings") {
            Chip(
                onClick = onSettingsClick,
                label = { Text("Settings") },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatItem(
    chat: ChatUiItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val preview = remember(chat.lastMessage, chat.isFromMe) {
        if (chat.isFromMe) "You: ${chat.lastMessage}" else chat.lastMessage
    }

    Chip(
        onClick = onClick,
        label = {
            Text(
                text = chat.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (chat.hasUnread) FontWeight.Bold else FontWeight.Normal
            )
        },
        secondaryLabel = {
            Text(
                text = preview,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = ChipDefaults.secondaryChipColors()
    )
}
