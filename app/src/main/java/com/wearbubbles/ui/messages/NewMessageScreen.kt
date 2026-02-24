package com.wearbubbles.ui.messages

import android.app.RemoteInput
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults.ItemType
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState

@Composable
fun NewMessageScreen(
    onRecipientSelected: (address: String) -> Unit
) {
    val addressLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results = RemoteInput.getResultsFromIntent(result.data ?: return@rememberLauncherForActivityResult)
        val addr = results.getCharSequence("address")?.toString() ?: ""
        if (addr.isNotBlank()) onRecipientSelected(addr)
    }

    // Automatically launch the input on first composition
    var launched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
        }
    }

    val columnState = rememberResponsiveColumnState(
        contentPadding = ScalingLazyColumnDefaults.padding(
            first = ItemType.Text,
            last = ItemType.Chip
        )
    )

    ScreenScaffold(scrollState = columnState) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState
        ) {
            item(key = "header") {
                Text(
                    text = "New Message",
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary
                )
            }

            item(key = "to") {
                Spacer(modifier = Modifier.height(4.dp))
                Chip(
                    onClick = {
                        val remoteInputs = listOf(
                            RemoteInput.Builder("address")
                                .setLabel("Phone or email")
                                .wearableExtender {
                                    setEmojisAllowed(false)
                                    setInputActionType(EditorInfo.IME_ACTION_DONE)
                                }
                                .build()
                        )
                        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent().also {
                            RemoteInputIntentHelper.putRemoteInputsExtra(it, remoteInputs)
                        }
                        addressLauncher.launch(intent)
                    },
                    label = { Text("Enter phone or email") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}
