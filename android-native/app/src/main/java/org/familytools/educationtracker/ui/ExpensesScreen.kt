@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.familytools.educationtracker.data.ExpenseRow

@Composable
fun ExpensesScreen(viewModel: ExpenseViewModel, onBack: () -> Unit, onScanReceipt: () -> Unit) {
    val children by viewModel.children.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val total by viewModel.total.collectAsState()
    val selectedChildId by viewModel.selectedChildId.collectAsState()

    // Reusing the shared ViewModel across navigations meant a stale
    // selection from a previous visit (or from Scan Receipt, which shares
    // this same ViewModel) silently kept filtering the list even though the
    // dropdown looked blank on a fresh visit — reset every time this screen
    // is actually opened so "no selection" always means "show everyone".
    LaunchedEffect(Unit) { viewModel.clearSelectedChild() }

    val selectedChildName = children.firstOrNull { it.id == selectedChildId }?.fullName ?: ""
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseRow?>(null) }
    var error by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = onScanReceipt) { Icon(Icons.Filled.CameraAlt, contentDescription = "Scan Receipt") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (selectedChildName.isEmpty()) error = "Select a child first" else showAddDialog = true
            }) { Icon(Icons.Filled.Add, contentDescription = "Add expense") }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            EntityDropdownField(
                "Child (blank = all children)", children, selectedChildName, { it.fullName },
                { viewModel.selectChild(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (selectedChildName.isEmpty()) "All children — Total: Rs. %,.2f".format(total) else "Total spent: Rs. %,.2f".format(total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)

            LazyColumn {
                items(expenses, key = { it.id }) { e ->
                    val details = listOfNotNull(
                        e.expenseDate.ifBlank { null },
                        e.className?.ifBlank { null }?.let { "Class $it" },
                        e.description.ifBlank { null },
                    ).joinToString(" · ")
                    val headline = if (selectedChildName.isEmpty()) {
                        "${e.childName} — ${e.categoryName} — Rs. " + "%,.2f".format(e.amount)
                    } else {
                        "${e.categoryName} — Rs. " + "%,.2f".format(e.amount)
                    }
                    ListItem(
                        headlineContent = { Text(headline) },
                        supportingContent = { Text(details.ifBlank { "No details" }) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editingExpense = e }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = { viewModel.deleteExpense(e.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        ExpenseFormDialog(
            title = "Add Expense",
            categories = categories.map { it.name },
            initial = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { category, amount, date, description, year, className ->
                viewModel.addExpense(
                    category, amount, date, description, year, receiptPath = "", className = className,
                    onDone = { showAddDialog = false },
                    onError = { msg -> error = msg },
                )
            },
        )
    }

    editingExpense?.let { row ->
        ExpenseFormDialog(
            title = "Edit Expense",
            categories = categories.map { it.name },
            initial = row,
            onDismiss = { editingExpense = null },
            onConfirm = { category, amount, date, description, year, className ->
                viewModel.updateExpense(
                    row.id, row.childId, category, amount, date, description, year, className = className,
                    onDone = { editingExpense = null },
                    onError = { msg -> error = msg },
                )
            },
        )
    }
}

@Composable
private fun ExpenseFormDialog(
    title: String,
    categories: List<String>,
    initial: ExpenseRow?,
    onDismiss: () -> Unit,
    onConfirm: (category: String, amount: String, date: String, description: String, year: String, className: String) -> Unit,
) {
    var category by remember { mutableStateOf(initial?.categoryName ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var date by remember { mutableStateOf(initial?.expenseDate ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var year by remember { mutableStateOf(initial?.yearLabel ?: "") }
    var className by remember { mutableStateOf(initial?.className ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                EntityDropdownField("Category *", categories, category, { it }, { category = it }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(year, { year = it }, label = { Text("Academic Year (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(className, { className = it }, label = { Text("Class (optional, e.g. III)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(category, amount, date, description, year, className) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
