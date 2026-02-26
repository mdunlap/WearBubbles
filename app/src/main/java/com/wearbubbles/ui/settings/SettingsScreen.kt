package com.wearbubbles.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults.ItemType
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState

@Composable
fun SettingsScreen(
    onReset: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val hapticEnabled by viewModel.hapticEnabled.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val isConnected = viewModel.isConnected
    val updateAvailable by viewModel.updateAvailable.collectAsStateWithLifecycle()
    val currentVersion = viewModel.currentVersion

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
                    text = "Settings",
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center
                )
            }

            // Haptics toggle
            item(key = "haptic_toggle") {
                ToggleChip(
                    checked = hapticEnabled,
                    onCheckedChange = { viewModel.toggleHaptic() },
                    label = { Text("Haptic feedback") },
                    secondaryLabel = {
                        Text(if (hapticEnabled) "Vibrate on new messages" else "Off")
                    },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(hapticEnabled),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Server status
            item(key = "server_status") {
                Chip(
                    onClick = {},
                    label = {
                        Text(
                            text = if (serverUrl.isNotBlank()) {
                                serverUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
                            } else {
                                "Not configured"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Row {
                            Text(
                                text = if (isConnected) "\u2022 Connected" else "\u2022 Disconnected",
                                color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                                fontSize = 12.sp
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }

            // Version info
            item(key = "version") {
                Chip(
                    onClick = {},
                    label = { Text("v$currentVersion") },
                    secondaryLabel = {
                        Text(
                            if (updateAvailable != null) "v$updateAvailable available \u2014 update via phone or GitHub"
                            else "Up to date"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }

            // Reset watch
            item(key = "reset") {
                var confirmReset by remember { mutableStateOf(false) }

                if (confirmReset) {
                    Chip(
                        onClick = {
                            confirmReset = false
                            viewModel.resetWatch(onReset)
                        },
                        label = { Text("Confirm reset?") },
                        secondaryLabel = { Text("Tap to confirm") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.chipColors(
                            backgroundColor = MaterialTheme.colors.error
                        )
                    )
                } else {
                    Chip(
                        onClick = { confirmReset = true },
                        label = { Text("Reset watch") },
                        secondaryLabel = { Text("Clear all data & sign out") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }
        }
    }
}
