package com.wearbubbles.ui.setup

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults.ItemType
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState

@Composable
fun SetupScreen(
    onConnected: () -> Unit,
    viewModel: SetupViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected) {
            onConnected()
        }
    }

    val urlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results = RemoteInput.getResultsFromIntent(result.data ?: return@rememberLauncherForActivityResult)
        val url = results.getCharSequence("server_url")?.toString() ?: ""
        viewModel.onServerUrlChanged(url)
    }

    val passwordLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results = RemoteInput.getResultsFromIntent(result.data ?: return@rememberLauncherForActivityResult)
        val pwd = results.getCharSequence("password")?.toString() ?: ""
        viewModel.onPasswordChanged(pwd)
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
        item {
            Text(
                text = "WearBubbles",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary
            )
        }

        item {
            Text(
                text = "Enter credentials below or use the WearBubbles companion app on your phone.",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Chip(
                onClick = {
                    val intent = createRemoteInputIntent(
                        "server_url",
                        "Server URL",
                        uiState.serverUrl
                    )
                    urlLauncher.launch(intent)
                },
                label = {
                    Text(
                        text = if (uiState.serverUrl.isBlank()) "Server URL" else uiState.serverUrl,
                        maxLines = 2
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors()
            )
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Chip(
                onClick = {
                    val intent = createRemoteInputIntent(
                        "password",
                        "Password",
                        uiState.password
                    )
                    passwordLauncher.launch(intent)
                },
                label = {
                    Text(
                        text = if (uiState.password.isBlank()) "Password" else "\u2022".repeat(uiState.password.length),
                        maxLines = 1
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors()
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Chip(
                    onClick = { viewModel.connect() },
                    label = { Text("Connect") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        }

        uiState.error?.let { error ->
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
    }
}

private fun createRemoteInputIntent(
    key: String,
    label: String,
    currentValue: String
): Intent {
    val remoteInputs = listOf(
        RemoteInput.Builder(key)
            .setLabel(label)
            .wearableExtender {
                setEmojisAllowed(false)
                setInputActionType(EditorInfo.IME_ACTION_DONE)
            }
            .build()
    )
    return RemoteInputIntentHelper.createActionRemoteInputIntent().also {
        RemoteInputIntentHelper.putRemoteInputsExtra(it, remoteInputs)
    }
}
