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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import org.familytools.educationtracker.services.NameMatcher
import org.familytools.educationtracker.services.OcrService
import java.io.File

@Composable
fun ScanReportScreen(viewModel: AcademicRecordsViewModel, onBack: () -> Unit) {
    val children by viewModel.children.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedChildName by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var term by remember { mutableStateOf("") }
    var examType by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf("") }
    var daysPresent by remember { mutableStateOf("") }
    var workingDays by remember { mutableStateOf("") }
    var teacherRemarksText by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(listOf(MarkFormRow())) }
    var coCurricularRows by remember { mutableStateOf(listOf<MarkFormRow>()) }
    var status by remember { mutableStateOf("") }
    var rawText by remember { mutableStateOf("") }
    var showRawText by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun doSave(force: Boolean) {
        viewModel.saveExam(
            year, className, section, term, examType, examDate, rows,
            coCurricularRows = coCurricularRows,
            attendanceDaysPresent = daysPresent.toIntOrNull(),
            attendanceWorkingDays = workingDays.toIntOrNull(),
            teacherRemarks = teacherRemarksText,
            force = force,
            onDone = {
                rows = listOf(MarkFormRow())
                coCurricularRows = emptyList()
                status = ""
                showDuplicateDialog = false
                scope.launch { snackbarHostState.showSnackbar("Report saved") }
            },
            onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
            onDuplicate = { showDuplicateDialog = true },
        )
    }

    suspend fun runOcr(uri: Uri) {
        status = "Processing image..."
        try {
            val ocr = OcrService.recognize(context, uri)
            rawText = ocr.fullText
            val parsed = OcrService.parseProgressReport(ocr.fullText, ocr.rows)

            var matchedChild = NameMatcher.findBestMatch(children, parsed.studentName)
            var createdNewChild = false
            if (matchedChild == null && parsed.studentName.isNotBlank()) {
                matchedChild = viewModel.findOrCreateChildByName(
                    name = parsed.studentName,
                    schoolName = parsed.schoolName,
                    admissionNumber = parsed.registerNo,
                    currentClass = parsed.className,
                    section = parsed.section,
                    academicYear = parsed.academicYear,
                )
                createdNewChild = true
            }
            if (matchedChild != null) {
                selectedChildName = matchedChild.fullName
                viewModel.selectChild(matchedChild.id)
            }
            if (parsed.academicYear.isNotBlank()) year = parsed.academicYear
            if (parsed.className.isNotBlank()) className = parsed.className
            if (parsed.section.isNotBlank()) section = parsed.section
            if (parsed.examType.isNotBlank()) { term = parsed.examType; examType = parsed.examType }
            if (parsed.examDate.isNotBlank()) examDate = parsed.examDate
            if (parsed.attendanceDaysPresent != null) daysPresent = parsed.attendanceDaysPresent.toString()
            if (parsed.attendanceWorkingDays != null) workingDays = parsed.attendanceWorkingDays.toString()
            if (parsed.teacherRemarks.isNotBlank()) teacherRemarksText = parsed.teacherRemarks

            if (parsed.subjectRows.isNotEmpty()) {
                rows = parsed.subjectRows.map {
                    MarkFormRow(
                        subject = it.subject,
                        marksObtained = it.marksObtained?.toString() ?: "",
                        maxMarks = it.maxMarks?.toString() ?: "",
                        grade = it.grade,
                        rank = it.rank?.toString() ?: "",
                        remarks = it.remarks,
                    )
                }
            }
            if (parsed.coCurricularRows.isNotEmpty()) {
                coCurricularRows = parsed.coCurricularRows.map {
                    MarkFormRow(subject = it.subject, grade = it.grade)
                }
            }

            val childNote = when {
                createdNewChild -> "Added new child: ${matchedChild?.fullName}."
                matchedChild != null -> "Matched child: ${matchedChild.fullName}."
                parsed.studentName.isNotBlank() -> "Couldn't match \"${parsed.studentName}\" to an enrolled child — select manually."
                else -> "Couldn't read a student name — select the child manually."
            }
            status = if (parsed.subjectRows.isNotEmpty()) {
                "$childNote Extracted ${parsed.subjectRows.size} subject row(s) — please verify before saving."
            } else {
                "$childNote Couldn't automatically parse subject rows — please enter marks manually."
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
            val file = File(context.cacheDir, "captures").apply { mkdirs() }.resolve("capture_${System.currentTimeMillis()}.jpg")
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
                title = { Text("Scan Report") },
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
                { selectedChildName = it.fullName; viewModel.selectChild(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Exam Context", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(year, { year = it }, label = { Text("Academic Year *") }, modifier = Modifier.weight(1f))
                OutlinedTextField(className, { className = it }, label = { Text("Class *") }, modifier = Modifier.weight(1f))
                OutlinedTextField(section, { section = it }, label = { Text("Section") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(term, { term = it }, label = { Text("Term *") }, modifier = Modifier.weight(1f))
                OutlinedTextField(examType, { examType = it }, label = { Text("Exam Type *") }, modifier = Modifier.weight(1f))
                OutlinedTextField(examDate, { examDate = it }, label = { Text("Exam Date") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    daysPresent, { daysPresent = it.filter { c -> c.isDigit() } },
                    label = { Text("Days Present") }, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    workingDays, { workingDays = it.filter { c -> c.isDigit() } },
                    label = { Text("Working Days") }, modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                teacherRemarksText, { teacherRemarksText = it },
                label = { Text("Teacher's Remarks") }, modifier = Modifier.fillMaxWidth(),
            )

            Text("Capture Report", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        val file = File(context.cacheDir, "captures").apply { mkdirs() }.resolve("capture_${System.currentTimeMillis()}.jpg")
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
            if (rawText.isNotEmpty()) {
                TextButton(onClick = { showRawText = !showRawText }) {
                    Text(if (showRawText) "Hide raw OCR text" else "Show raw OCR text")
                }
                if (showRawText) {
                    Text(
                        rawText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subjects & Marks (verify before saving)", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { rows = rows + MarkFormRow() }) { Text("+ Add Row") }
            }
            MarksTableEditor(
                rows = rows,
                onRowChange = { i, r -> rows = rows.toMutableList().also { it[i] = r } },
                onRemoveRow = { i -> rows = rows.toMutableList().also { it.removeAt(i) } },
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Co-Curricular Activities & Character Traits", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { coCurricularRows = coCurricularRows + MarkFormRow() }) { Text("+ Add Row") }
            }
            MarksTableEditor(
                rows = coCurricularRows,
                onRowChange = { i, r -> coCurricularRows = coCurricularRows.toMutableList().also { it[i] = r } },
                onRemoveRow = { i -> coCurricularRows = coCurricularRows.toMutableList().also { it.removeAt(i) } },
            )

            Button(onClick = { doSave(force = false) }, modifier = Modifier.fillMaxWidth()) { Text("Save Report") }
        }
    }

    if (showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text("Report already scanned") },
            text = {
                Text(
                    "A report for $selectedChildName — $year, $className${if (section.isNotBlank()) " - $section" else ""}, " +
                        "$term / $examType already has marks recorded. Save anyway to update it?",
                )
            },
            confirmButton = { TextButton(onClick = { doSave(force = true) }) { Text("Save Anyway") } },
            dismissButton = { TextButton(onClick = { showDuplicateDialog = false }) { Text("Cancel") } },
        )
    }
}
