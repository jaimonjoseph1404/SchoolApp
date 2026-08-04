@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import org.familytools.educationtracker.data.ChildDocument
import java.io.File

@Composable
fun DocumentsScreen(viewModel: DocumentsViewModel, onBack: () -> Unit) {
    val children by viewModel.children.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedChildId by remember { mutableStateOf<Long?>(null) }
    var selectedChildName by remember { mutableStateOf("") }
    var academicYear by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("CERTIFICATE") }
    var title by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun save(uri: Uri) {
        val childId = selectedChildId
        if (childId == null) { status = "Select a child first"; return }
        viewModel.addDocument(
            context = context, childId = childId, academicYear = academicYear, category = category, title = title,
            sourceUri = uri,
            onDone = { status = "Saved"; title = ""; scope.launch { snackbarHostState.showSnackbar("Saved") } },
            onError = { msg -> status = msg },
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) save(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) save(uri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "captures").apply { mkdirs() }.resolve("doc_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            status = "Camera permission denied — use Gallery instead."
        }
    }

    fun launchCamera() {
        if (selectedChildId == null) { status = "Select a child first"; return }
        if (academicYear.isBlank()) { status = "Enter an academic year first"; return }
        val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val file = File(context.cacheDir, "captures").apply { mkdirs() }.resolve("doc_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    fun launchGallery() {
        if (selectedChildId == null) { status = "Select a child first"; return }
        if (academicYear.isBlank()) { status = "Enter an academic year first"; return }
        galleryLauncher.launch("image/*")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Certificates & Photos") },
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
            OutlinedTextField(
                academicYear, { academicYear = it }, label = { Text("Academic Year (e.g. 2025-26) *") },
                modifier = Modifier.fillMaxWidth(),
            )
            EntityDropdownField(
                "Type", listOf("CERTIFICATE", "CLASS_PHOTO"),
                if (category == "CERTIFICATE") "Certificate" else "Class Photo",
                { if (it == "CERTIFICATE") "Certificate" else "Class Photo" },
                { category = it },
                modifier = Modifier.fillMaxWidth(),
            )
            if (category == "CERTIFICATE") {
                OutlinedTextField(
                    title, { title = it }, label = { Text("Title (e.g. 1st Prize - Elocution)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launchCamera() }) { Text("Camera") }
                Button(onClick = { launchGallery() }) { Text("Gallery") }
            }
            if (status.isNotEmpty()) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (selectedChildId == null) {
                Text("Select a child to see saved certificates and class photos.", style = MaterialTheme.typography.bodySmall)
            } else if (documents.isEmpty()) {
                Text("No certificates or class photos saved yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                val grouped = documents.groupBy { it.academicYear }.toSortedMap(compareByDescending { it })
                grouped.forEach { (year, docsForYear) ->
                    Text(year, style = MaterialTheme.typography.titleMedium)
                    docsForYear.forEach { doc ->
                        DocumentRow(doc, onDelete = { viewModel.deleteDocument(doc) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentRow(document: ChildDocument, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        DocumentThumbnail(document.imagePath, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (document.category == "CLASS_PHOTO") "Class Photo" else "Certificate",
                style = MaterialTheme.typography.labelMedium,
            )
            if (document.title.isNotBlank()) Text(document.title, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
    }
}

@Composable
private fun DocumentThumbnail(path: String, modifier: Modifier = Modifier) {
    // No image-loading library in this project (org.json-only philosophy
    // extends to avoiding a new dependency for a single thumbnail view) —
    // a downsampled BitmapFactory decode is plenty for a 56dp thumbnail.
    val bitmap = remember(path) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
