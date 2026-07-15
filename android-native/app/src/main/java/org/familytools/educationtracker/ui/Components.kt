package org.familytools.educationtracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A read-only text field that opens a dropdown of [items] on tap — used for
 * Child/Teacher/Category selection throughout. A transparent overlay Box
 * captures the tap since a read-only OutlinedTextField otherwise intercepts
 * touch for its own cursor handling. */
@Composable
fun <T> EntityDropdownField(
    label: String,
    items: List<T>,
    selectedLabel: String,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { if (items.isNotEmpty()) expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(itemLabel(item)) }, onClick = { onSelect(item); expanded = false })
            }
        }
    }
}

data class MarkFormRow(
    var subject: String = "",
    var marksObtained: String = "",
    var maxMarks: String = "",
    var grade: String = "",
    var rank: String = "",
    var remarks: String = "",
)

@Composable
fun MarksTableEditor(
    rows: List<MarkFormRow>,
    onRowChange: (Int, MarkFormRow) -> Unit,
    onRemoveRow: (Int) -> Unit,
) {
    Column {
        rows.forEachIndexed { index, row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = row.subject,
                    onValueChange = { onRowChange(index, row.copy(subject = it)) },
                    label = { Text("Subject") },
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = row.marksObtained,
                    onValueChange = { onRowChange(index, row.copy(marksObtained = it.filter { c -> c.isDigit() || c == '.' })) },
                    label = { Text("Marks") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.maxMarks,
                    onValueChange = { onRowChange(index, row.copy(maxMarks = it.filter { c -> c.isDigit() || c == '.' })) },
                    label = { Text("Max") },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = row.grade,
                    onValueChange = { onRowChange(index, row.copy(grade = it)) },
                    label = { Text("Grade") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.rank,
                    onValueChange = { onRowChange(index, row.copy(rank = it.filter { c -> c.isDigit() })) },
                    label = { Text("Rank") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.remarks,
                    onValueChange = { onRowChange(index, row.copy(remarks = it)) },
                    label = { Text("Remarks") },
                    modifier = Modifier.weight(2f),
                )
                IconButton(onClick = { onRemoveRow(index) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove row")
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}
