@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
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
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Academic & Expense Reports", style = MaterialTheme.typography.titleMedium)
            EntityDropdownField(
                "Child", children, selectedChild?.fullName ?: "", { it.fullName }, { selectedChild = it },
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

            Text("Teacher Effectiveness Report", style = MaterialTheme.typography.titleMedium)
            EntityDropdownField(
                "Teacher", teachers, selectedTeacher?.name ?: "", { it.name }, { selectedTeacher = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val teacher = selectedTeacher ?: return@Button notify("Select a teacher first")
                    viewModel.generateTeacherPdf(context, teacher.id, teacher.name) { notify("Saved: ${it.name}") }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Teacher Effectiveness (PDF)") }

            Text(
                "Reports are saved to the app's private external storage folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
