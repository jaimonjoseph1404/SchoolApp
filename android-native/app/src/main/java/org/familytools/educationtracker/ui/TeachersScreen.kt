@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.familytools.educationtracker.data.Teacher

@Composable
fun TeachersScreen(
    viewModel: TeacherViewModel,
    onBack: () -> Unit,
    onAddTeacher: () -> Unit,
    onEditTeacher: (Teacher) -> Unit,
) {
    val teachers by viewModel.teachers.collectAsState()
    val assignments by viewModel.assignments.collectAsState()
    val children by viewModel.children.collectAsState()
    var showAssignDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teachers") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTeacher) { Icon(Icons.Filled.Add, contentDescription = "Add teacher") }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (teachers.isEmpty()) {
                Text(
                    "No teachers added yet. Tap + to add one.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(teachers, key = { it.id }) { teacher ->
                        ListItem(
                            headlineContent = { Text(teacher.name) },
                            supportingContent = { Text(teacher.subject.ifBlank { "No subject set" }) },
                            leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                            modifier = Modifier.clickable { onEditTeacher(teacher) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Subject Assignments", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showAssignDialog = true }) { Text("+ Assign") }
            }
            LazyColumn {
                items(assignments, key = { it.id }) { a ->
                    ListItem(
                        headlineContent = { Text("${a.teacherName} → ${a.subjectName}") },
                        supportingContent = { Text("${a.childName} · ${a.yearLabel} · ${a.className}") },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteAssignment(a.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove")
                            }
                        },
                    )
                }
            }
        }
    }

    if (showAssignDialog) {
        AssignDialog(
            children = children,
            teachers = teachers,
            onDismiss = { showAssignDialog = false },
            onConfirm = { childId, year, className, subject, teacherId, onError ->
                viewModel.assign(childId, year, className, subject, teacherId, onDone = { showAssignDialog = false }, onError = onError)
            },
        )
    }
}

@Composable
private fun AssignDialog(
    children: List<org.familytools.educationtracker.data.Child>,
    teachers: List<Teacher>,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, String, String, Long, (String) -> Unit) -> Unit,
) {
    var selectedChild by remember { mutableStateOf<org.familytools.educationtracker.data.Child?>(null) }
    var selectedTeacher by remember { mutableStateOf<Teacher?>(null) }
    var year by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Teacher") },
        text = {
            Column {
                EntityDropdownField(
                    "Child", children, selectedChild?.fullName ?: "",
                    { it.fullName }, { selectedChild = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(year, { year = it }, label = { Text("Academic Year *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(className, { className = it }, label = { Text("Class *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subject, { subject = it }, label = { Text("Subject *") }, modifier = Modifier.fillMaxWidth())
                EntityDropdownField(
                    "Teacher", teachers, selectedTeacher?.name ?: "",
                    { it.name }, { selectedTeacher = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val childId = selectedChild?.id
                val teacherId = selectedTeacher?.id
                if (childId == null || teacherId == null) {
                    error = "All fields are required"
                } else {
                    onConfirm(childId, year, className, subject, teacherId) { msg -> error = msg }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
