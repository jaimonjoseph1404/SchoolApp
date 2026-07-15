@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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

@Composable
fun ExpensesScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    val children by viewModel.children.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val total by viewModel.total.collectAsState()

    var selectedChildName by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
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
                "Child", children, selectedChildName, { it.fullName },
                { selectedChildName = it.fullName; viewModel.selectChild(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (selectedChildName.isEmpty()) "Select a child to view expenses" else "Total spent: Rs. %,.2f".format(total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)

            LazyColumn {
                items(expenses, key = { it.id }) { e ->
                    val details = listOfNotNull(e.expenseDate.ifBlank { null }, e.description.ifBlank { null }).joinToString(" · ")
                    ListItem(
                        headlineContent = { Text("${e.categoryName} — Rs. %,.2f".format(e.amount)) },
                        supportingContent = { Text(details.ifBlank { "No details" }) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteExpense(e.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            categories = categories.map { it.name },
            onDismiss = { showAddDialog = false },
            onConfirm = { category, amount, date, description, year ->
                viewModel.addExpense(
                    category, amount, date, description, year, receiptPath = "",
                    onDone = { showAddDialog = false },
                    onError = { msg -> error = msg },
                )
            },
        )
    }
}

@Composable
private fun AddExpenseDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit,
) {
    var category by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Expense") },
        text = {
            Column {
                EntityDropdownField("Category *", categories, category, { it }, { category = it }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(year, { year = it }, label = { Text("Academic Year (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(category, amount, date, description, year) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
