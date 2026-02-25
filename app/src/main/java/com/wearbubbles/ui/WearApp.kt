package com.wearbubbles.ui

import android.util.Base64
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.google.android.horologist.compose.layout.AppScaffold
import com.wearbubbles.ui.conversations.ConversationListScreen
import com.wearbubbles.ui.conversations.ConversationListViewModel
import com.wearbubbles.ui.messages.MessageDetailScreen
import com.wearbubbles.ui.messages.NewMessageScreen
import com.wearbubbles.ui.settings.SettingsScreen
import com.wearbubbles.ui.setup.SetupScreen
import com.wearbubbles.ui.theme.WearBubblesTheme

private fun encodeGuid(guid: String): String =
    Base64.encodeToString(guid.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)

private fun decodeGuid(encoded: String): String =
    String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP))

@Composable
fun WearApp(hasCredentials: Boolean, openChatGuid: String? = null) {
    WearBubblesTheme {
        AppScaffold {
            val navController = rememberSwipeDismissableNavController()
            val startDestination = if (hasCredentials) "conversations" else "setup"

            val conversationListViewModel: ConversationListViewModel = viewModel()

            // Navigate to chat if opened from notification
            LaunchedEffect(openChatGuid) {
                if (openChatGuid != null && hasCredentials) {
                    val encoded = encodeGuid(openChatGuid)
                    navController.navigate("messages/$encoded")
                }
            }

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
                            navController.navigate("settings")
                        },
                        onNewMessageClick = {
                            navController.navigate("new_message")
                        },
                        viewModel = conversationListViewModel
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        onReset = {
                            navController.navigate("setup") {
                                popUpTo(0)
                            }
                        }
                    )
                }

                composable("new_message") {
                    NewMessageScreen(
                        onRecipientSelected = { address ->
                            val chatGuid = "iMessage;-;$address"
                            val encoded = encodeGuid(chatGuid)
                            navController.navigate("messages/$encoded") {
                                popUpTo("new_message") { inclusive = true }
                            }
                        }
                    )
                }

                composable("messages/{chatGuid}") { backStackEntry ->
                    val encodedGuid = backStackEntry.arguments?.getString("chatGuid") ?: ""
                    val chatGuid = try {
                        decodeGuid(encodedGuid)
                    } catch (_: Exception) {
                        encodedGuid
                    }

                    val chats by conversationListViewModel.uiState.collectAsStateWithLifecycle()
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
