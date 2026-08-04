@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import org.familytools.educationtracker.services.AiModel
import org.familytools.educationtracker.services.AiModelManager
import org.familytools.educationtracker.services.DownloadProgress

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

            HorizontalDivider()
            AiModelsSection(viewModel)
            HorizontalDivider()

            Text(
                "Full-database encryption at rest would require a native SQLCipher build; " +
                    "PIN/biometric app-lock plus AES-256 encrypted backups (see Backup) cover " +
                    "access control for now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "Education Performance & Cost Tracker\nVersion ${org.familytools.educationtracker.BuildConfig.VERSION_NAME}",
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

@Composable
private fun AiModelsSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val aiScanningEnabled by viewModel.isAiScanningEnabled.collectAsState()
    var textReady by remember { mutableStateOf(AiModelManager.isReady(context, AiModel.TEXT)) }
    var visionReady by remember { mutableStateOf(AiModelManager.isReady(context, AiModel.VISION)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AI-Assisted Scanning", style = MaterialTheme.typography.titleMedium)
        Text(
            "Runs an on-device AI model (LiteRT-LM) to read scanned reports more " +
                "reliably than plain OCR pattern-matching. Each model is 0.6-2.6GB — " +
                "import a copy you already have, or download once (Wi-Fi recommended). " +
                "The app keeps working with standard OCR if a model isn't ready.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AiModelRow(model = AiModel.TEXT, ready = textReady, onReadyChange = { textReady = it })
        AiModelRow(model = AiModel.VISION, ready = visionReady, onReadyChange = { visionReady = it })

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Use AI-assisted scanning", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = aiScanningEnabled && (textReady || visionReady),
                enabled = textReady || visionReady,
                onCheckedChange = { viewModel.setAiScanningEnabled(it) },
            )
        }
        if (!textReady && !visionReady) {
            Text(
                "Import or download at least one model above to enable this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiModelRow(model: AiModel, ready: Boolean, onReadyChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var progress by remember { mutableStateOf<DownloadProgress?>(null) }
    var importing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            errorText = ""
            scope.launch {
                val success = AiModelManager.importFromUri(context, model, uri)
                importing = false
                if (success) {
                    onReadyChange(true)
                } else {
                    errorText = "Import failed — make sure the file is a complete .litertlm model."
                }
            }
        }
    }

    val activeDownloadId = downloadId
    if (activeDownloadId != null) {
        LaunchedEffect(activeDownloadId) {
            AiModelManager.observeDownloadProgress(context, activeDownloadId).collect { p ->
                progress = p
                if (p.isDone) {
                    downloadId = null
                    onReadyChange(true)
                } else if (p.isFailed) {
                    downloadId = null
                    errorText = "Download failed — try again on Wi-Fi, or use Import instead."
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(model.label, style = MaterialTheme.typography.bodyLarge)
        val sizeMb = model.approxSizeBytes / (1024 * 1024)
        val statusText = when {
            ready -> "Ready"
            importing -> "Importing…"
            progress != null -> "Downloading… ${(progress!!.fraction * 100).toInt()}%"
            else -> "Not downloaded (~${sizeMb}MB)"
        }
        Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (progress != null && !ready) {
            LinearProgressIndicator(progress = { progress!!.fraction }, modifier = Modifier.fillMaxWidth())
        }
        if (errorText.isNotEmpty()) {
            Text(errorText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (!ready) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { errorText = ""; importLauncher.launch(arrayOf("*/*")) },
                    enabled = !importing && progress == null,
                ) { Text("Import file") }
                Button(
                    onClick = { errorText = ""; downloadId = AiModelManager.enqueueDownload(context, model) },
                    enabled = !importing && progress == null,
                ) { Text("Download") }
            }
        }
    }
}
