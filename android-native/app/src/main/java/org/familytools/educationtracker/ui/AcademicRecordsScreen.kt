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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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

            Text("Recent Marks", style = MaterialTheme.typography.titleMedium)
            history.takeLast(30).reversed().forEach { r ->
                val pctText = r.percentage?.let { " (%.1f%%)".format(it) } ?: ""
                ListItem(
                    headlineContent = { Text("${r.subjectName}: ${r.marksObtained}/${r.maxMarks}$pctText") },
                    supportingContent = { Text("${r.yearLabel} · ${r.className} · ${r.termName} · ${r.examType}") },
                )
            }
        }
    }
}
