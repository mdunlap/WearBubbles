package com.wearbubbles.ui.conversations

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*

@Composable
fun ConversationListScreen(
    onChatClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ConversationListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialize()
        viewModel.refresh()
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 40.dp,
            start = 8.dp,
            end = 8.dp,
            bottom = 16.dp
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary
            )
        }

        if (uiState.isLoading && uiState.chats.isEmpty()) {
            item {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        uiState.error?.let { error ->
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption3,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        if (uiState.chats.isEmpty() && !uiState.isLoading) {
            item {
                Text(
                    text = "No conversations",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }

        items(uiState.chats.size) { index ->
            val chat = uiState.chats[index]
            ChatItem(
                chat = chat,
                onClick = { onChatClick(chat.guid) }
            )
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Chip(
                onClick = onSettingsClick,
                label = { Text("Settings") },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

@Composable
private fun ChatItem(
    chat: ChatUiItem,
    onClick: () -> Unit
) {
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
            val preview = if (chat.isFromMe) "You: ${chat.lastMessage}" else chat.lastMessage
            Text(
                text = preview,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors()
    )
}
