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
import androidx.compose.material3.MaterialTheme
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
import org.familytools.educationtracker.data.Child

@Composable
fun ChildFormScreen(
    child: Child,
    onBack: () -> Unit,
    onSave: (Child) -> Unit,
    onDelete: (Child) -> Unit,
) {
    var fullName by remember { mutableStateOf(child.fullName) }
    var gender by remember { mutableStateOf(child.gender) }
    var dateOfBirth by remember { mutableStateOf(child.dateOfBirth) }
    var schoolName by remember { mutableStateOf(child.schoolName) }
    var admissionNumber by remember { mutableStateOf(child.admissionNumber) }
    var currentClass by remember { mutableStateOf(child.currentClass) }
    var section by remember { mutableStateOf(child.section) }
    var academicYear by remember { mutableStateOf(child.academicYear) }
    var bloodGroup by remember { mutableStateOf(child.bloodGroup) }
    var parentNotes by remember { mutableStateOf(child.parentNotes) }
    var medicalNotes by remember { mutableStateOf(child.medicalNotes) }
    var interests by remember { mutableStateOf(child.interests) }
    var careerAspiration by remember { mutableStateOf(child.careerAspiration) }
    var nameError by remember { mutableStateOf(false) }

    val isEditing = child.id != 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Child" else "Add Child") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Personal Information", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it; nameError = false },
                label = { Text("Full Name *") },
                isError = nameError,
                supportingText = { if (nameError) Text("Full name is required") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(gender, { gender = it }, label = { Text("Gender") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                dateOfBirth, { dateOfBirth = it },
                label = { Text("Date of Birth (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                schoolName, { schoolName = it },
                label = { Text("School Name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                admissionNumber, { admissionNumber = it },
                label = { Text("Admission Number") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                currentClass, { currentClass = it },
                label = { Text("Current Class") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                section, { section = it },
                label = { Text("Section") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                academicYear, { academicYear = it },
                label = { Text("Academic Year (e.g. 2026-2027)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Optional Information", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                bloodGroup, { bloodGroup = it },
                label = { Text("Blood Group") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                parentNotes, { parentNotes = it },
                label = { Text("Parent Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                medicalNotes, { medicalNotes = it },
                label = { Text("Medical Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                interests, { interests = it },
                label = { Text("Interests") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                careerAspiration, { careerAspiration = it },
                label = { Text("Career Aspiration") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (isEditing) {
                    TextButton(onClick = { onDelete(child) }) { Text("Delete") }
                } else {
                    Text("")
                }
                Button(onClick = {
                    if (fullName.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    onSave(
                        child.copy(
                            fullName = fullName.trim(),
                            gender = gender.trim(),
                            dateOfBirth = dateOfBirth.trim(),
                            schoolName = schoolName.trim(),
                            admissionNumber = admissionNumber.trim(),
                            currentClass = currentClass.trim(),
                            section = section.trim(),
                            academicYear = academicYear.trim(),
                            bloodGroup = bloodGroup.trim(),
                            parentNotes = parentNotes.trim(),
                            medicalNotes = medicalNotes.trim(),
                            interests = interests.trim(),
                            careerAspiration = careerAspiration.trim(),
                        ),
                    )
                }) { Text("Save") }
            }
        }
    }
}
