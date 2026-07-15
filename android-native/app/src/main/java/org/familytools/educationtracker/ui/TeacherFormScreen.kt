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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.familytools.educationtracker.data.Teacher

@Composable
fun TeacherFormScreen(
    teacher: Teacher,
    onBack: () -> Unit,
    onSave: (Teacher) -> Unit,
    onDelete: (Teacher) -> Unit,
) {
    var name by remember { mutableStateOf(teacher.name) }
    var subject by remember { mutableStateOf(teacher.subject) }
    var qualification by remember { mutableStateOf(teacher.qualification) }
    var experience by remember { mutableStateOf(teacher.experience) }
    var phone by remember { mutableStateOf(teacher.phone) }
    var email by remember { mutableStateOf(teacher.email) }
    var schoolName by remember { mutableStateOf(teacher.schoolName) }
    var notes by remember { mutableStateOf(teacher.notes) }
    var nameError by remember { mutableStateOf(false) }
    val isEditing = teacher.id != 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Teacher" else "Add Teacher") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                name, { name = it; nameError = false },
                label = { Text("Teacher Name *") }, isError = nameError, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(qualification, { qualification = it }, label = { Text("Qualification") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(experience, { experience = it }, label = { Text("Experience") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(schoolName, { schoolName = it }, label = { Text("School") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())

            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (isEditing) TextButton(onClick = { onDelete(teacher) }) { Text("Delete") } else Text("")
                Button(onClick = {
                    if (name.isBlank()) { nameError = true; return@Button }
                    onSave(
                        teacher.copy(
                            name = name.trim(), subject = subject.trim(), qualification = qualification.trim(),
                            experience = experience.trim(), phone = phone.trim(), email = email.trim(),
                            schoolName = schoolName.trim(), notes = notes.trim(),
                        ),
                    )
                }) { Text("Save") }
            }
        }
    }
}
