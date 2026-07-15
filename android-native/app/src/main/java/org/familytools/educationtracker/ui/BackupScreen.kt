@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.familytools.educationtracker.services.BackupService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun refreshStatus() {
        statusText = BackupService.lastBackupPath(context)?.let { (path, ts) ->
            "Last backup: ${File(path).name} at ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ts))}"
        } ?: "No backups yet."
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

            Text("Export", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = { BackupService.exportJson(context); refreshStatus(); notify("Exported JSON backup") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export JSON Backup") }
            Button(
                onClick = { BackupService.exportZip(context); refreshStatus(); notify("Exported ZIP backup") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export ZIP Backup") }
            Button(
                onClick = { passwordDialogMode = "export"; showPasswordDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export Encrypted Backup (AES-256)") }

            Text("Import", style = MaterialTheme.typography.titleMedium)
            Text(
                "Restoring replaces all current data with the backup's contents.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("Import Backup File")
            }
        }
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
                            BackupService.exportEncrypted(context, password)
                            refreshStatus()
                            notify("Encrypted backup saved")
                        } else {
                            val uri = pendingImportUri
                            if (uri != null) {
                                val tmp = File.createTempFile("import", ".bak", context.cacheDir)
                                context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                                BackupService.importEncrypted(context, tmp, password)
                                notify("Restored from encrypted backup")
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
