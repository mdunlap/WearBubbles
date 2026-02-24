package com.wearbubbles.ui.messages

import android.app.RemoteInput
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.wearbubbles.ui.theme.BlueBubble
import com.wearbubbles.ui.theme.GrayBubble
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageDetailScreen(
    chatGuid: String,
    chatName: String,
    socketManager: com.wearbubbles.socket.SocketManager?,
    viewModel: MessageDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(chatGuid) {
        viewModel.initialize(chatGuid, socketManager)
    }

    val replyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results = RemoteInput.getResultsFromIntent(result.data ?: return@rememberLauncherForActivityResult)
        val reply = results.getCharSequence("reply")?.toString() ?: ""
        if (reply.isNotBlank()) {
            viewModel.sendMessage(reply)
        }
    }

    val listState = rememberScalingLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            // Last item index = messages + header + reply chip
            listState.animateScrollToItem(uiState.messages.size)
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
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
                text = chatName,
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }

        if (uiState.isLoading && uiState.messages.isEmpty()) {
            item {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        items(uiState.messages.size) { index ->
            val message = uiState.messages[index]
            MessageBubble(message = message)
        }

        // Reply chip
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Chip(
                onClick = {
                    val remoteInputs = listOf(
                        RemoteInput.Builder("reply")
                            .setLabel("Reply")
                            .wearableExtender {
                                setEmojisAllowed(true)
                                setInputActionType(EditorInfo.IME_ACTION_SEND)
                            }
                            .build()
                    )
                    val intent = RemoteInputIntentHelper.createActionRemoteInputIntent().also {
                        RemoteInputIntentHelper.putRemoteInputsExtra(it, remoteInputs)
                    }
                    replyLauncher.launch(intent)
                },
                label = {
                    Text(
                        text = if (uiState.isSending) "Sending..." else "Reply",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(),
                enabled = !uiState.isSending
            )
        }

        uiState.error?.let { error ->
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption3,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageUiItem) {
    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isFromMe) BlueBubble else GrayBubble
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (message.isFromMe) 16.dp else 4.dp,
        bottomEnd = if (message.isFromMe) 4.dp else 16.dp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = message.text,
                color = MaterialTheme.colors.onPrimary,
                fontSize = 13.sp
            )
            Text(
                text = formatTime(message.dateCreated),
                color = MaterialTheme.colors.onPrimary.copy(alpha = 0.6f),
                fontSize = 10.sp,
                textAlign = if (message.isFromMe) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val date = Date(timestamp)
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        diff < 604_800_000 -> SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}
