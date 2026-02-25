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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults.ItemType
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.wearbubbles.ui.theme.BlueBubble
import com.wearbubbles.ui.theme.GrayBubble
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

// Pre-allocate shapes (immutable, thread-safe)
private val SentBubbleShape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
private val ReceivedBubbleShape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
private val ImageClipShape = RoundedCornerShape(12.dp)

// Thread-safe DateTimeFormatters (unlike SimpleDateFormat)
private val TimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val DayTimeFormatter = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)
private val DateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val localZone = ZoneId.systemDefault()

@Composable
fun MessageDetailScreen(
    chatGuid: String,
    chatName: String,
    socketManager: com.wearbubbles.socket.SocketManager?,
    viewModel: MessageDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

    val columnState = rememberResponsiveColumnState(
        contentPadding = ScalingLazyColumnDefaults.padding(
            first = ItemType.Text,
            last = ItemType.SingleButton
        )
    )

    // Auto-scroll to reply chip when messages change (skip bulk loads like "load earlier")
    val messageCount = uiState.messages.size
    var lastScrolledCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(messageCount) {
        if (messageCount > 0 && messageCount != lastScrolledCount) {
            val delta = messageCount - lastScrolledCount
            lastScrolledCount = messageCount
            // Only auto-scroll on initial load or small deltas (1-3 new messages)
            if (delta in 1..3 || delta == messageCount) {
                // Scroll past messages to the reply chip so it's centered on screen
                // Items: header + (load_earlier?) + messages + reply + (error?)
                val replyIndex = 1 + (if (uiState.hasMoreMessages) 1 else 0) + messageCount
                columnState.state.scrollToItem(replyIndex)
            }
        }
    }

    ScreenScaffold(scrollState = columnState) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState
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

            if (uiState.hasMoreMessages && uiState.messages.isNotEmpty()) {
                item(key = "load_earlier") {
                    Chip(
                        onClick = { viewModel.loadMore() },
                        label = {
                            if (uiState.isLoadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Load earlier",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors(),
                        enabled = !uiState.isLoadingMore
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

    // Pre-compute modifiers to avoid allocation in composition
    val textPadding = if (hasAttachment) Modifier.padding(horizontal = 6.dp) else Modifier

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Box {
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
                        modifier = textPadding
                    )
                }

                Text(
                    text = remember(message.dateCreated) { formatTime(message.dateCreated) },
                    color = MaterialTheme.colors.onPrimary.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    textAlign = if (message.isFromMe) TextAlign.End else TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(textPadding)
                )
            }

            if (message.hasLoveReaction) {
                Text(
                    text = "❤️",
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 2.dp)
                )
            }
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

    val imageRequest = remember(attachmentGuid) {
        val url = "${serverUrl.trimEnd('/')}/api/v1/attachment/$attachmentGuid/download?password=$password"
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .size(250, 250)
            .apply {
                if (isGif) decoderFactory(GifDecoder.Factory())
            }
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp, max = 120.dp)
            .clip(ImageClipShape),
        contentScale = ContentScale.Crop
    )
}

// Thread-safe time formatting using java.time (no SimpleDateFormat)
private fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val diff = System.currentTimeMillis() - timestamp

    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        else -> {
            val instant = Instant.ofEpochMilli(timestamp)
            val dateTime = instant.atZone(localZone)
            when {
                diff < 86_400_000 -> TimeFormatter.format(dateTime)
                diff < 604_800_000 -> DayTimeFormatter.format(dateTime)
                else -> DateFormatter.format(dateTime)
            }
        }
    }
}
