@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AcademicRecordsScreen(viewModel: AcademicRecordsViewModel, onBack: () -> Unit) {
    val children by viewModel.children.collectAsState()
    val selectedChildId by viewModel.selectedChildId.collectAsState()
    val history by viewModel.marksHistory.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedChildName by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var term by remember { mutableStateOf("") }
    var examType by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(listOf(MarkFormRow())) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Academic Records") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EntityDropdownField(
                label = "Child",
                items = children,
                selectedLabel = selectedChildName,
                itemLabel = { it.fullName },
                onSelect = { selectedChildName = it.fullName; viewModel.selectChild(it.id) },
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

            Text("Subjects & Marks", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    val childId = selectedChildId
                    if (childId == null || className.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("Select a child and class first") }
                    } else {
                        scope.launch {
                            val template = viewModel.getTemplate(childId, className, "SUBJECT")
                            if (template.isEmpty()) {
                                snackbarHostState.showSnackbar("No saved subjects for this class yet")
                            } else {
                                val present = rows.map { it.subject.trim().uppercase() }.toSet()
                                val missing = template.filter { it.trim().uppercase() !in present }
                                rows = rows.filter { it.subject.isNotBlank() } + missing.map { MarkFormRow(subject = it) }
                            }
                        }
                    }
                }) { Text("Use Template") }
                TextButton(onClick = { rows = rows + MarkFormRow() }) { Text("+ Add Row") }
            }
            MarksTableEditor(
                rows = rows,
                onRowChange = { i, r -> rows = rows.toMutableList().also { it[i] = r } },
                onRemoveRow = { i -> rows = rows.toMutableList().also { it.removeAt(i) } },
            )

            androidx.compose.material3.Button(
                onClick = {
                    viewModel.saveExam(
                        year, className, section, term, examType, examDate, rows,
                        onDone = {
                            rows = listOf(MarkFormRow())
                            scope.launch { snackbarHostState.showSnackbar("Marks saved") }
                        },
                        onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save Marks") }

            Text("Complete Mark History", style = MaterialTheme.typography.titleMedium)
            if (history.isEmpty()) {
                Text("No academic records yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                MarkHistoryByYear(history)
            }
        }
    }
}

/** Groups the full (uncapped) mark history by year, then by exam (term +
 * exam type + class), so a parent can see every subject's actual score for
 * a specific term rather than a flat, most-recent-30 list that could hide
 * an entire earlier term. */
@Composable
private fun MarkHistoryByYear(history: List<org.familytools.educationtracker.data.MarkHistoryRow>) {
    val byYear = history.groupBy { it.yearLabel }.toSortedMap(compareByDescending { it })
    byYear.forEach { (year, yearRows) ->
        Text(year.ifBlank { "Year not set" }, style = MaterialTheme.typography.titleSmall)
        val byExam = yearRows.groupBy { Triple(it.className, it.termName, it.examType) }
        byExam.forEach { (key, examRows) ->
            val (className, termName, examType) = key
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "$termName — $examType (Class $className)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    examRows.forEach { r ->
                        val marks = if (r.marksObtained != null && r.maxMarks != null) {
                            "${r.marksObtained}/${r.maxMarks}"
                        } else {
                            "-"
                        }
                        val pctText = r.percentage?.let { " (%.1f%%)".format(it) } ?: ""
                        Text(
                            "${r.subjectName}: $marks$pctText ${r.grade.ifBlank { "" }}".trim(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    val first = examRows.first()
                    val total = if (first.totalMarksObtained != null && first.totalMaxMarks != null) {
                        "Total: ${first.totalMarksObtained}/${first.totalMaxMarks}"
                    } else {
                        null
                    }
                    val attendance = if (first.attendanceDaysPresent != null && first.attendanceWorkingDays != null) {
                        "Attendance: ${first.attendanceDaysPresent}/${first.attendanceWorkingDays}"
                    } else {
                        null
                    }
                    listOfNotNull(total, attendance).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (first.teacherRemarks.isNotBlank()) {
                        Text(
                            "Remarks: ${first.teacherRemarks}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
