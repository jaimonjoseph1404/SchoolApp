@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val isDark by viewModel.isDarkTheme.collectAsState()
    val pinEnabled by viewModel.isPinLockEnabled.collectAsState()
    val biometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Dark Mode", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isDark, onCheckedChange = { viewModel.setDarkTheme(it) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PIN Lock", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = pinEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            pinInput = ""
                            showPinDialog = true
                        } else {
                            viewModel.disablePin()
                        }
                    },
                )
            }

            if (pinEnabled) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Biometric Unlock (fingerprint/face)", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && activity != null && isBiometricAvailable(activity)) {
                                viewModel.setBiometricEnabled(true)
                            } else if (!enabled) {
                                viewModel.setBiometricEnabled(false)
                            }
                        },
                    )
                }
                TextButton(onClick = { pinInput = ""; showPinDialog = true }) { Text("Change PIN") }
            }

            Text(
                "Full-database encryption at rest would require a native SQLCipher build; " +
                    "PIN/biometric app-lock plus AES-256 encrypted backups (see Backup) cover " +
                    "access control for now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "Education Performance & Cost Tracker\nVersion 0.1.0 (Native, Phase 1)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set PIN") },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("New 4-6 digit PIN") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput.length >= 4) {
                        viewModel.setPin(pinInput) { showPinDialog = false }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            },
        )
    }
}
