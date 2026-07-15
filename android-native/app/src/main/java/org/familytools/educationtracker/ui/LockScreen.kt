package org.familytools.educationtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

@Composable
fun LockScreen(viewModel: SettingsViewModel, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val biometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    fun tryBiometric() {
        if (activity != null && biometricEnabled && isBiometricAvailable(activity)) {
            showBiometricPrompt(activity, onSuccess = onUnlocked, onError = { })
        }
    }

    LaunchedEffect(biometricEnabled) { tryBiometric() }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp))
            Text("Enter PIN to unlock", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.padding(8.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6); error = "" },
                label = { Text("PIN") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (error.isNotEmpty()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.padding(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        if (viewModel.verifyPin(pin)) onUnlocked() else { error = "Incorrect PIN"; pin = "" }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Unlock") }

            if (biometricEnabled) {
                Spacer(modifier = Modifier.padding(4.dp))
                OutlinedButton(onClick = { tryBiometric() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Use biometric unlock")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
