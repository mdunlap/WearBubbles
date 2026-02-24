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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import coil.compose.SubcomposeAsyncImage
import coil.decode.GifDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.wearbubbles.ui.theme.BlueBubble
import com.wearbubbles.ui.theme.GrayBubble
import java.text.SimpleDateFormat
import java.util.*

// Pre-allocate shapes to avoid reallocation on every recompose
private val SentBubbleShape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
private val ReceivedBubbleShape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
private val ImageClipShape = RoundedCornerShape(12.dp)

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

    // Auto-scroll to bottom only on first load or when sending
    val messageCount = uiState.messages.size
    var lastScrolledCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(messageCount) {
        if (messageCount > 0 && messageCount != lastScrolledCount) {
            lastScrolledCount = messageCount
            listState.scrollToItem(messageCount)
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
        item(key = "header") {
            Text(
                text = chatName,
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }

        if (uiState.isLoading && uiState.messages.isEmpty()) {
            item(key = "loading") {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        items(
            count = uiState.messages.size,
            key = { uiState.messages[it].guid }
        ) { index ->
            val message = uiState.messages[index]
            MessageBubble(
                message = message,
                serverUrl = uiState.serverUrl,
                password = uiState.password
            )
        }

        // Reply chip
        item(key = "reply") {
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
            item(key = "error") {
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
private fun MessageBubble(
    message: MessageUiItem,
    serverUrl: String,
    password: String
) {
    val hasAttachment = message.attachmentGuid != null
    val hasText = message.text.isNotBlank()
    if (!hasText && !hasAttachment) return

    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isFromMe) BlueBubble else GrayBubble
    val shape = if (message.isFromMe) SentBubbleShape else ReceivedBubbleShape

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
                .padding(
                    horizontal = if (hasAttachment) 4.dp else 10.dp,
                    vertical = if (hasAttachment) 4.dp else 6.dp
                )
        ) {
            if (hasAttachment) {
                AttachmentImage(
                    serverUrl = serverUrl,
                    password = password,
                    attachmentGuid = message.attachmentGuid!!,
                    mimeType = message.attachmentMimeType
                )
                if (hasText) Spacer(modifier = Modifier.height(4.dp))
            }

            if (hasText) {
                Text(
                    text = message.text,
                    color = MaterialTheme.colors.onPrimary,
                    fontSize = 13.sp,
                    modifier = if (hasAttachment) Modifier.padding(horizontal = 6.dp) else Modifier
                )
            }

            Text(
                text = remember(message.dateCreated) { formatTime(message.dateCreated) },
                color = MaterialTheme.colors.onPrimary.copy(alpha = 0.6f),
                fontSize = 10.sp,
                textAlign = if (message.isFromMe) TextAlign.End else TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (hasAttachment) Modifier.padding(horizontal = 6.dp) else Modifier)
            )
        }
    }
}

@Composable
private fun AttachmentImage(
    serverUrl: String,
    password: String,
    attachmentGuid: String,
    mimeType: String?
) {
    val context = LocalContext.current
    val isGif = mimeType == "image/gif"

    // Remember the image request to avoid rebuilding on recompose
    val imageRequest = remember(attachmentGuid) {
        val url = "${serverUrl.trimEnd('/')}/api/v1/attachment/$attachmentGuid/download?password=$password&height=150&quality=good"
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(200)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .size(300, 300)
            .apply {
                if (isGif) decoderFactory(GifDecoder.Factory())
            }
            .build()
    }

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp, max = 120.dp)
            .clip(ImageClipShape),
        contentScale = ContentScale.Crop,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    )
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
