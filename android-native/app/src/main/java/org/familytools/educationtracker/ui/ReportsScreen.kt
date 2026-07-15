@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.launch

@Composable
fun ReportsScreen(viewModel: ReportsViewModel, onBack: () -> Unit) {
    val children by viewModel.children.collectAsState()
    val teachers by viewModel.teachers.collectAsState()
    val academicPreview by viewModel.academicPreview.collectAsState()
    val expensePreview by viewModel.expensePreview.collectAsState()
    val teacherPreview by viewModel.teacherPreview.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedChild by remember { mutableStateOf<org.familytools.educationtracker.data.Child?>(null) }
    var selectedTeacher by remember { mutableStateOf<org.familytools.educationtracker.data.Teacher?>(null) }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Academic & Expense Reports", style = MaterialTheme.typography.titleMedium)
            EntityDropdownField(
                "Child", children, selectedChild?.fullName ?: "", { it.fullName },
                { selectedChild = it; viewModel.loadChildPreview(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val child = selectedChild ?: return@Button notify("Select a child first")
                    viewModel.generateAcademicPdf(context, child.id, child.fullName) { notify("Saved: ${it.name}") }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Academic Year Summary (PDF)") }
            Button(
                onClick = {
                    val child = selectedChild ?: return@Button notify("Select a child first")
                    viewModel.generateExpenseCsv(context, child.id, child.fullName) { notify("Saved: ${it.name}") }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Expense Report (CSV)") }

            if (selectedChild != null) {
                Text("Marks — ${selectedChild!!.fullName}", style = MaterialTheme.typography.titleSmall)
                if (academicPreview.isEmpty()) {
                    Text("No academic records yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    academicPreview.forEach { row ->
                        Text(
                            "${row.yearLabel} · ${row.className} · ${row.termName} · ${row.examType} — " +
                                "${row.subjectName}: ${row.marksObtained?.toInt() ?: "-"}/${row.maxMarks?.toInt() ?: "-"} " +
                                "(${row.grade.ifBlank { "-" }})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Expenses — ${selectedChild!!.fullName}", style = MaterialTheme.typography.titleSmall)
                if (expensePreview.isEmpty()) {
                    Text("No expenses recorded yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    expensePreview.forEach { row ->
                        Text(
                            "${row.expenseDate.ifBlank { "-" }} · ${row.categoryName} — Rs. %,.2f".format(row.amount),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            Text("Teacher Effectiveness Report", style = MaterialTheme.typography.titleMedium)
            EntityDropdownField(
                "Teacher", teachers, selectedTeacher?.name ?: "", { it.name },
                { selectedTeacher = it; viewModel.loadTeacherPreview(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val teacher = selectedTeacher ?: return@Button notify("Select a teacher first")
                    viewModel.generateTeacherPdf(context, teacher.id, teacher.name) { notify("Saved: ${it.name}") }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Teacher Effectiveness (PDF)") }

            if (selectedTeacher != null) {
                Text("Effectiveness — ${selectedTeacher!!.name}", style = MaterialTheme.typography.titleSmall)
                if (teacherPreview.isEmpty()) {
                    Text("No assigned marks yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    teacherPreview.forEach { row ->
                        Text(
                            "${row.yearLabel}: avg ${row.avgPercentage?.let { "%.1f%%".format(it) } ?: "-"} " +
                                "across ${row.markCount} mark(s)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Text(
                "PDF/CSV exports are saved to the app's private external storage folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
