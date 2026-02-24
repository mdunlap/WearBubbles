package com.wearbubbles.ui

import android.util.Base64
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.wearbubbles.ui.conversations.ConversationListScreen
import com.wearbubbles.ui.conversations.ConversationListViewModel
import com.wearbubbles.ui.messages.MessageDetailScreen
import com.wearbubbles.ui.setup.SetupScreen
import com.wearbubbles.ui.theme.WearBubblesTheme

private fun encodeGuid(guid: String): String =
    Base64.encodeToString(guid.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)

private fun decodeGuid(encoded: String): String =
    String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP))

@Composable
fun WearApp(hasCredentials: Boolean) {
    WearBubblesTheme {
        Scaffold(
            timeText = { TimeText() }
        ) {
            val navController = rememberSwipeDismissableNavController()
            val startDestination = if (hasCredentials) "conversations" else "setup"

            val conversationListViewModel: ConversationListViewModel = viewModel()

            SwipeDismissableNavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
            ) {
                composable("setup") {
                    SetupScreen(
                        onConnected = {
                            navController.navigate("conversations") {
                                popUpTo("setup") { inclusive = true }
                            }
                        }
                    )
                }

                composable("conversations") {
                    ConversationListScreen(
                        onChatClick = { chatGuid ->
                            val encoded = encodeGuid(chatGuid)
                            navController.navigate("messages/$encoded")
                        },
                        onSettingsClick = {
                            navController.navigate("setup")
                        },
                        viewModel = conversationListViewModel
                    )
                }

                composable("messages/{chatGuid}") { backStackEntry ->
                    val encodedGuid = backStackEntry.arguments?.getString("chatGuid") ?: ""
                    val chatGuid = try {
                        decodeGuid(encodedGuid)
                    } catch (_: Exception) {
                        encodedGuid
                    }

                    val chats by conversationListViewModel.uiState.collectAsState()
                    val chatName = chats.chats.find { it.guid == chatGuid }?.displayName ?: chatGuid

                    MessageDetailScreen(
                        chatGuid = chatGuid,
                        chatName = chatName,
                        socketManager = conversationListViewModel.getSocketManager()
                    )
                }
            }
        }
    }
}
