@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import org.familytools.educationtracker.services.ExtractedReceipt
import org.familytools.educationtracker.services.NameMatcher
import org.familytools.educationtracker.services.OcrService
import java.io.File

@Composable
fun ScanReceiptScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    val children by viewModel.children.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedChildId by remember { mutableStateOf<Long?>(null) }
    var selectedChildName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var schoolName by remember { mutableStateOf("") }
    var receiptNumber by remember { mutableStateOf("") }
    var imagePath by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    suspend fun runOcr(uri: Uri) {
        status = "Processing image..."
        try {
            imagePath = uri.toString()
            val text = OcrService.extractText(context, uri)
            val receipt: ExtractedReceipt = OcrService.parseReceiptText(text)

            val matchedChild = NameMatcher.findBestMatch(children, receipt.studentName)
            if (matchedChild != null) {
                selectedChildId = matchedChild.id
                selectedChildName = matchedChild.fullName
                viewModel.selectChild(matchedChild.id)
            }
            schoolName = receipt.schoolName
            receiptNumber = receipt.receiptNumber
            if (receipt.receiptDate.isNotBlank()) date = receipt.receiptDate
            if (receipt.totalAmount != null) amount = receipt.totalAmount.toString()
            if (receipt.suggestedCategory.isNotBlank()) category = receipt.suggestedCategory

            val childNote = when {
                matchedChild != null -> "Matched child: ${matchedChild.fullName}."
                receipt.studentName.isNotBlank() -> "Couldn't match \"${receipt.studentName}\" — select the child manually."
                else -> "Couldn't read a student name — select the child manually."
            }
            status = if (receipt.totalAmount != null) {
                "$childNote Extracted amount ₹${receipt.totalAmount} — please verify before saving."
            } else {
                "$childNote Couldn't read an amount — please enter it manually."
            }
        } catch (e: Exception) {
            status = "OCR failed: ${e.message}"
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { runOcr(uri) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) scope.launch { runOcr(uri) }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "captures").apply { mkdirs() }.resolve("receipt_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            status = "Camera permission denied — use Gallery instead."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Fee Receipt") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EntityDropdownField(
                "Child", children, selectedChildName, { it.fullName },
                { selectedChildId = it.id; selectedChildName = it.fullName; viewModel.selectChild(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Capture Receipt", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        val file = File(context.cacheDir, "captures").apply { mkdirs() }.resolve("receipt_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    } else {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                }) { Text("Camera") }
                Button(onClick = { galleryLauncher.launch("image/*") }) { Text("Gallery") }
            }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("Receipt Details (verify before saving)", style = MaterialTheme.typography.titleMedium)
            EntityDropdownField("Category", categories.map { it.name }, category, { it }, { category = it }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(date, { date = it }, label = { Text("Date") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(schoolName, { schoolName = it }, label = { Text("School / Description") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(receiptNumber, { receiptNumber = it }, label = { Text("Receipt Number") }, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = {
                    val childId = selectedChildId
                    if (childId == null) {
                        scope.launch { snackbarHostState.showSnackbar("Select a child first") }
                        return@Button
                    }
                    viewModel.addExpenseFromReceipt(
                        childId = childId,
                        category = category,
                        receipt = ExtractedReceipt(
                            schoolName = schoolName, receiptNumber = receiptNumber, receiptDate = date,
                            totalAmount = amount.toDoubleOrNull(),
                        ),
                        imagePath = imagePath,
                        onDone = {
                            amount = ""; date = ""; schoolName = ""; receiptNumber = ""; status = ""
                            scope.launch { snackbarHostState.showSnackbar("Expense saved from receipt") }
                        },
                        onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save Expense") }
        }
    }
}
