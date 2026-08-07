@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.launch
import org.familytools.educationtracker.services.BackupService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(settingsViewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val backupFolderUri by settingsViewModel.backupFolderUri.collectAsState()

    var statusText by remember {
        mutableStateOf(
            BackupService.lastBackupPath(context)?.let { (path, ts) ->
                "Last backup: ${File(path).name} at ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ts))}"
            } ?: "No backups yet.",
        )
    }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordDialogMode by remember { mutableStateOf("export") } // "export" | "import"
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportFile by remember { mutableStateOf<File?>(null) }
    var showImportChooser by remember { mutableStateOf(false) }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun refreshStatus() {
        statusText = BackupService.lastBackupPath(context)?.let { (path, ts) ->
            "Last backup: ${File(path).name} at ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ts))}"
        } ?: "No backups yet."
    }

    fun afterExport(file: File, label: String) {
        BackupService.copyToExternalFolder(context, file, backupFolderUri)
        refreshStatus()
        val savedNote = if (backupFolderUri.isNotBlank()) " (also saved to your chosen folder)" else ""
        notify("$label$savedNote")
    }

    fun importFromFile(file: File) {
        try {
            when {
                file.name.endsWith(".json") -> {
                    BackupService.importJson(context, file)
                    notify("Restored from JSON backup")
                }
                file.name.endsWith(".zip") -> {
                    BackupService.importZip(context, file)
                    notify("Restored from ZIP backup")
                }
                file.name.endsWith(".bak") -> {
                    pendingImportFile = file
                    pendingImportUri = null
                    passwordDialogMode = "import"
                    showPasswordDialog = true
                }
                else -> notify("Unrecognized backup file type")
            }
        } catch (e: Exception) {
            notify("Restore failed: ${e.message}")
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            settingsViewModel.setBackupFolderUri(uri.toString())
            notify("Backup folder set — future exports are also saved there")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment ?: ""
        try {
            when {
                name.endsWith(".json") -> {
                    val tmp = File.createTempFile("import", ".json", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    BackupService.importJson(context, tmp)
                    notify("Restored from JSON backup")
                }
                name.endsWith(".zip") -> {
                    val tmp = File.createTempFile("import", ".zip", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    BackupService.importZip(context, tmp)
                    notify("Restored from ZIP backup")
                }
                name.endsWith(".bak") -> {
                    pendingImportUri = uri
                    pendingImportFile = null
                    passwordDialogMode = "import"
                    showPasswordDialog = true
                }
                else -> notify("Unrecognized backup file type")
            }
        } catch (e: Exception) {
            notify("Restore failed: ${e.message}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(statusText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("Backup Folder", style = MaterialTheme.typography.titleMedium)
            Text(
                if (backupFolderUri.isBlank()) {
                    "Not set — backups are only kept in this app's private storage, which is deleted if you " +
                        "uninstall the app. Choose a folder (e.g. in your phone's Downloads or a synced Drive " +
                        "folder) to also save every export there."
                } else {
                    "Saving to: ${DocumentFile.fromTreeUri(context, Uri.parse(backupFolderUri))?.name ?: backupFolderUri}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { folderPickerLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (backupFolderUri.isBlank()) "Choose Backup Folder" else "Change Backup Folder")
            }

            HorizontalDivider()

            Text("Export", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = { afterExport(BackupService.exportJson(context), "Exported JSON backup") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export JSON Backup") }
            Button(
                onClick = { afterExport(BackupService.exportZip(context), "Exported ZIP backup") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export ZIP Backup") }
            Button(
                onClick = { passwordDialogMode = "export"; showPasswordDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export Encrypted Backup (AES-256)") }

            HorizontalDivider()

            Text("Import", style = MaterialTheme.typography.titleMedium)
            Text(
                "Restoring replaces all current data with the backup's contents.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { showImportChooser = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Import Backup File")
            }
        }
    }

    if (showImportChooser) {
        val localBackups = remember(showImportChooser) { BackupService.listLocalBackups(context) }
        AlertDialog(
            onDismissRequest = { showImportChooser = false },
            title = { Text("Import Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (localBackups.isEmpty()) {
                        Text(
                            "No backups saved on this device yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Backups saved on this device (folder where backups are saved):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        localBackups.forEach { file ->
                            TextButton(
                                onClick = { showImportChooser = false; importFromFile(file) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "${file.name} — ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(file.lastModified()))}",
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportChooser = false; importLauncher.launch("*/*") }) {
                    Text("Browse Other Location…")
                }
            },
            dismissButton = { TextButton(onClick = { showImportChooser = false }) { Text("Cancel") } },
        )
    }

    if (showPasswordDialog) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(if (passwordDialogMode == "export") "Set a backup password" else "Enter backup password") },
            text = {
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, label = { Text("Password") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    if (password.isBlank()) { notify("Password cannot be empty"); return@TextButton }
                    try {
                        if (passwordDialogMode == "export") {
                            afterExport(BackupService.exportEncrypted(context, password), "Encrypted backup saved")
                        } else {
                            val uri = pendingImportUri
                            val localFile = pendingImportFile
                            when {
                                localFile != null -> {
                                    BackupService.importEncrypted(context, localFile, password)
                                    notify("Restored from encrypted backup")
                                }
                                uri != null -> {
                                    val tmp = File.createTempFile("import", ".bak", context.cacheDir)
                                    context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                                    BackupService.importEncrypted(context, tmp, password)
                                    notify("Restored from encrypted backup")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        notify(e.message ?: "Operation failed")
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") } },
        )
    }
}
