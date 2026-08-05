@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.familytools.educationtracker.services.AiModel
import org.familytools.educationtracker.services.AiModelManager
import org.familytools.educationtracker.services.AiReportParser
import org.familytools.educationtracker.services.GeminiReportParser
import org.familytools.educationtracker.services.NameMatcher
import org.familytools.educationtracker.services.OcrService
import org.familytools.educationtracker.services.ParsedReportCard
import org.familytools.educationtracker.services.combineScannedPages
import org.familytools.educationtracker.services.mergeReportCards

@Composable
fun ScanReportScreen(viewModel: AcademicRecordsViewModel, settingsViewModel: SettingsViewModel, onBack: () -> Unit) {
    val children by viewModel.children.collectAsState()
    val selectedChildId by viewModel.selectedChildId.collectAsState()
    val aiScanningEnabled by settingsViewModel.isAiScanningEnabled.collectAsState()
    val geminiApiKey by settingsViewModel.geminiApiKey.collectAsState()
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
    var totalMarksObtained by remember { mutableStateOf("") }
    var totalMaxMarks by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(listOf(MarkFormRow())) }
    var coCurricularRows by remember { mutableStateOf(listOf<MarkFormRow>()) }
    var status by remember { mutableStateOf("") }
    var rawText by remember { mutableStateOf("") }
    var showRawText by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showPartTwoRescanDialog by remember { mutableStateOf(false) }
    var templateAutoLoaded by remember { mutableStateOf(false) }
    // A scanned name that didn't match an existing child is only *staged*
    // here — the Child row itself isn't created until Save is actually
    // pressed, so cancelling out of a scan (back button, another scan,
    // leaving the screen) never writes anything to the database.
    var pendingNewChildName by remember { mutableStateOf("") }
    var pendingNewChildSchool by remember { mutableStateOf("") }
    var pendingNewChildAdmissionNumber by remember { mutableStateOf("") }

    // Adds any template subject/activity not already present as a blank row
    // (marks left for the user to fill in) — used both to auto-complete an
    // OCR pass that missed some subjects, and to pre-fill a fully manual
    // entry once a child + class are known.
    fun mergeWithTemplate(existing: List<MarkFormRow>, template: List<String>): List<MarkFormRow> {
        if (template.isEmpty()) return existing
        val present = existing.map { it.subject.trim().uppercase() }.toSet()
        val missing = template.filter { it.trim().uppercase() !in present }
        val base = existing.filter { it.subject.isNotBlank() }
        return base + missing.map { MarkFormRow(subject = it) }
    }

    suspend fun loadTemplates(childId: Long, forClassName: String) {
        if (childId == 0L || forClassName.isBlank()) return
        val subjectTemplate = viewModel.getTemplate(childId, forClassName, "SUBJECT")
        if (subjectTemplate.isNotEmpty()) rows = mergeWithTemplate(rows, subjectTemplate)
        val coCurricularTemplate = viewModel.getTemplate(childId, forClassName, "COCURRICULAR")
        if (coCurricularTemplate.isNotEmpty()) coCurricularRows = mergeWithTemplate(coCurricularRows, coCurricularTemplate)
    }

    // Auto-fill once, the first time both a child and class are known and
    // nothing has been captured/typed yet — covers "open Scan Report and
    // pick a child/class" without requiring a photo or a button tap.
    LaunchedEffect(selectedChildId, className) {
        val childId = selectedChildId
        if (!templateAutoLoaded && childId != null && className.isNotBlank() &&
            rawText.isEmpty() && rows.size == 1 && rows[0].subject.isBlank() && coCurricularRows.isEmpty()
        ) {
            templateAutoLoaded = true
            loadTemplates(childId, className)
        }
    }

    fun doSave(force: Boolean) {
        scope.launch {
            // The one and only place a scanned-but-unmatched name actually
            // creates a Child record — deliberately gated behind the user
            // pressing Save, not merely scanning a photo.
            if (selectedChildId == null && pendingNewChildName.isNotBlank()) {
                val child = viewModel.findOrCreateChildByName(
                    name = pendingNewChildName,
                    schoolName = pendingNewChildSchool,
                    admissionNumber = pendingNewChildAdmissionNumber,
                    currentClass = className,
                    section = section,
                    academicYear = year,
                )
                selectedChildName = child.fullName
                viewModel.selectChild(child.id)
                pendingNewChildName = ""
            }
            viewModel.saveExam(
                year, className, section, term, examType, examDate, rows,
                coCurricularRows = coCurricularRows,
                attendanceDaysPresent = daysPresent.toIntOrNull(),
                attendanceWorkingDays = workingDays.toIntOrNull(),
                teacherRemarks = teacherRemarksText,
                totalMarksObtained = totalMarksObtained.toDoubleOrNull(),
                totalMaxMarks = totalMaxMarks.toDoubleOrNull(),
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
    }

    // Runs OCR (+ optional AI structuring) on a single captured page, without
    // touching any screen state — kept pure so runOcr() can call it once per
    // page and combine the results for a multi-page scan.
    suspend fun scanOneImage(uri: Uri): Pair<ParsedReportCard, String> {
        val ocr = OcrService.recognize(context, uri)
        var parsed = OcrService.parseProgressReport(ocr.fullText, ocr.rows)

        // AI-assisted structuring is additive: it only replaces `parsed`
        // if at least one pass actually produced a result, so a model
        // that isn't downloaded, times out, or fails to parse never
        // regresses below what the regex parser alone already found.
        val textReady = aiScanningEnabled && AiModelManager.isReady(context, AiModel.TEXT)
        val visionReady = aiScanningEnabled && AiModelManager.isReady(context, AiModel.VISION)
        val cloudReady = geminiApiKey.isNotBlank()
        if (textReady || visionReady || cloudReady) {
            val (aiText, aiImage, aiCloud) = coroutineScope {
                val textDeferred = if (textReady) async { AiReportParser.structureFromText(context, ocr.fullText) } else null
                val imageDeferred = if (visionReady) async { AiReportParser.structureFromImage(context, uri) } else null
                val cloudDeferred = if (cloudReady) async { GeminiReportParser.structureFromImage(context, uri, geminiApiKey) } else null
                Triple(textDeferred?.await(), imageDeferred?.await(), cloudDeferred?.await())
            }
            if (aiText != null || aiImage != null || aiCloud != null) {
                parsed = mergeReportCards(parsed, aiText, aiImage, aiCloud)
            }
        }
        return parsed to ocr.fullText
    }

    suspend fun runOcr(uris: List<Uri>) {
        if (uris.isEmpty()) return
        status = if (uris.size > 1) "Processing ${uris.size} pages..." else "Processing image..."
        try {
            val pageResults = uris.mapIndexed { index, uri ->
                if (uris.size > 1) status = "Processing page ${index + 1} of ${uris.size}..."
                scanOneImage(uri)
            }
            rawText = if (pageResults.size == 1) {
                pageResults[0].second
            } else {
                pageResults.mapIndexed { i, (_, text) -> "--- Page ${i + 1} ---\n$text" }.joinToString("\n\n")
            }
            // Cross-checks every page against every other: a field only one
            // page's angle/focus/lighting caught still comes through, and no
            // later (possibly worse) page can override an earlier good read.
            val parsed = combineScannedPages(pageResults.map { it.first })
            val aiUsed = geminiApiKey.isNotBlank() || (aiScanningEnabled &&
                (AiModelManager.isReady(context, AiModel.TEXT) || AiModelManager.isReady(context, AiModel.VISION)))

            val matchedChild = NameMatcher.findBestMatch(children, parsed.studentName)
            pendingNewChildName = ""
            if (matchedChild != null) {
                selectedChildName = matchedChild.fullName
                viewModel.selectChild(matchedChild.id)
            } else if (parsed.studentName.isNotBlank()) {
                // Stage only — the Child row is created at Save time, not
                // just from having scanned a photo (see doSave()).
                pendingNewChildName = parsed.studentName
                pendingNewChildSchool = parsed.schoolName
                pendingNewChildAdmissionNumber = parsed.registerNo
            }
            if (parsed.academicYear.isNotBlank()) year = parsed.academicYear
            if (parsed.className.isNotBlank()) className = parsed.className
            if (parsed.section.isNotBlank()) section = parsed.section
            if (parsed.examType.isNotBlank()) { term = parsed.examType; examType = parsed.examType }
            if (parsed.examDate.isNotBlank()) examDate = parsed.examDate
            if (parsed.attendanceDaysPresent != null) daysPresent = parsed.attendanceDaysPresent.toString()
            if (parsed.attendanceWorkingDays != null) workingDays = parsed.attendanceWorkingDays.toString()
            if (parsed.teacherRemarks.isNotBlank()) teacherRemarksText = parsed.teacherRemarks
            if (parsed.totalMarksObtained != null) totalMarksObtained = parsed.totalMarksObtained.toString()
            if (parsed.totalMaxMarks != null) totalMaxMarks = parsed.totalMaxMarks.toString()

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

            // Fill in any subject/activity the OCR pass missed from what was
            // saved for this child+class before — the accuracy floor for a
            // second-or-later scan of the same class is "we already know
            // the full list," even if this particular photo read badly.
            val effectiveClassName = parsed.className.ifBlank { className }
            var addedFromTemplate = 0
            if (matchedChild != null && effectiveClassName.isNotBlank()) {
                val beforeSubjectCount = rows.count { it.subject.isNotBlank() }
                val beforeCoCount = coCurricularRows.count { it.subject.isNotBlank() }
                loadTemplates(matchedChild.id, effectiveClassName)
                templateAutoLoaded = true
                addedFromTemplate = (rows.count { it.subject.isNotBlank() } - beforeSubjectCount) +
                    (coCurricularRows.count { it.subject.isNotBlank() } - beforeCoCount)
            }

            val childNote = when {
                matchedChild != null -> "Matched child: ${matchedChild.fullName}."
                pendingNewChildName.isNotBlank() ->
                    "New child \"$pendingNewChildName\" will be added when you press Save (or pick an existing child below)."
                parsed.studentName.isNotBlank() -> "Read a name but it didn't look valid — select the child manually."
                else -> "Couldn't read a student name — select the child manually."
            }
            val templateNote = if (addedFromTemplate > 0) {
                " Added $addedFromTemplate more from the saved template (OCR missed them) — fill in marks."
            } else {
                ""
            }
            val aiNote = if (aiUsed) " (AI-assisted)" else ""
            status = if (parsed.subjectRows.isNotEmpty()) {
                "$childNote Extracted ${parsed.subjectRows.size} subject row(s)$aiNote — please verify before saving.$templateNote"
            } else {
                "$childNote Couldn't automatically parse subject rows — please enter marks manually.$templateNote"
            }

            // Only nag about a missing Part-II on what looks like an actual
            // report scan (Part-I came through) — not on every blank/failed
            // photo, and not if a co-curricular section is already present
            // from this scan or a still-pending un-submitted earlier one.
            if (parsed.subjectRows.isNotEmpty() && coCurricularRows.none { it.subject.isNotBlank() }) {
                showPartTwoRescanDialog = true
            }
        } catch (e: Exception) {
            status = "OCR failed: ${e.message}"
        }
    }

    // Gallery import still accepts multiple images at once — picking several
    // existing photos of the same report gets the same cross-checked-across-
    // pages treatment as a live multi-page scan.
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) scope.launch { runOcr(uris) }
    }

    // Live document scanner (Google Play Services): a real camera
    // viewfinder with auto-focus and auto-capture-when-steady, automatic
    // edge detection + perspective correction, and support for capturing
    // several pages/attempts in one session — replaces a single static
    // photo, which is what was feeding low-quality images into OCR.
    val scannerClient = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(false) // the Gallery button already covers this
                .setPageLimit(5)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build(),
        )
    }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = result.data?.let { GmsDocumentScanningResult.fromActivityResultIntent(it) }?.pages?.map { it.imageUri }
            if (!uris.isNullOrEmpty()) {
                scope.launch { runOcr(uris) }
            } else {
                status = "No pages captured."
            }
        }
    }

    // Shared by the Live Scan button and the "Part-II missing, capture
    // another shot?" prompt below — both just need to (re)open the same
    // scanner session; runOcr()'s "only overwrite a field the new scan
    // actually found" behavior already makes a supplementary shot safe to
    // merge on top of what's already in the form.
    fun launchLiveScan() {
        val activity = context as? Activity
        if (activity == null) {
            status = "Live scanner unavailable here — use Gallery instead."
            return
        }
        scannerClient.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender -> scanLauncher.launch(IntentSenderRequest.Builder(intentSender).build()) }
            .addOnFailureListener { e -> status = "Live scanner unavailable (${e.message}) — use Gallery instead." }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    totalMarksObtained, { totalMarksObtained = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Total Marks Obtained") }, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    totalMaxMarks, { totalMaxMarks = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Total Max Marks") }, modifier = Modifier.weight(1f),
                )
            }

            Text("Capture Report", style = MaterialTheme.typography.titleMedium)
            Text(
                "Live Scan opens a real-time camera view — it auto-focuses, " +
                    "auto-captures once steady, crops/straightens the page, and " +
                    "lets you capture more than one page or retry a blurry shot " +
                    "in the same session before returning here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launchLiveScan() }) { Text("Live Scan") }
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

            Text("Subjects & Marks (verify before saving)", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    val childId = selectedChildId
                    if (childId == null || className.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("Select a child and class first") }
                    } else {
                        scope.launch { loadTemplates(childId, className) }
                    }
                }) { Text("Use Template") }
                TextButton(onClick = { rows = rows + MarkFormRow() }) { Text("+ Add Row") }
            }
            MarksTableEditor(
                rows = rows,
                onRowChange = { i, r -> rows = rows.toMutableList().also { it[i] = r } },
                onRemoveRow = { i -> rows = rows.toMutableList().also { it.removeAt(i) } },
            )

            Text("Co-Curricular Activities & Character Traits", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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

    if (showPartTwoRescanDialog) {
        AlertDialog(
            onDismissRequest = { showPartTwoRescanDialog = false },
            title = { Text("Part-II not captured") },
            text = {
                Text(
                    "Co-curricular activities (Part-II) weren't read from this scan. " +
                        "Capture another shot focused on that section? What's already " +
                        "filled in above won't be lost.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showPartTwoRescanDialog = false; launchLiveScan() }) { Text("Rescan") }
            },
            dismissButton = { TextButton(onClick = { showPartTwoRescanDialog = false }) { Text("Skip") } },
        )
    }
}
