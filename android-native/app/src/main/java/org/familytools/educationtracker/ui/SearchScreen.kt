@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.familytools.educationtracker.services.SearchResult
import org.familytools.educationtracker.services.SearchService

private fun iconFor(category: String): ImageVector = when (category) {
    "Child" -> Icons.Filled.Person
    "Subject" -> Icons.Filled.MenuBook
    "Teacher" -> Icons.Filled.SupervisorAccount
    "Academic Year" -> Icons.Filled.CalendarMonth
    "School" -> Icons.Filled.School
    "Expense Category" -> Icons.Filled.Payments
    else -> Icons.Filled.Person
}

@Composable
fun SearchScreen(onBack: () -> Unit, onOpenChild: (Long) -> Unit, onOpenTeacher: (Long) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; results = SearchService.search(context, it) },
                label = { Text("Search children, subjects, teachers, years, schools...") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            LazyColumn {
                items(results) { r ->
                    ListItem(
                        headlineContent = { Text(r.label) },
                        supportingContent = { Text(r.category + if (r.detail.isNotEmpty()) " · ${r.detail}" else "") },
                        leadingContent = { Icon(iconFor(r.category), contentDescription = null) },
                        modifier = Modifier.clickable {
                            val id = r.refId ?: return@clickable
                            when (r.category) {
                                "Child" -> onOpenChild(id)
                                "Teacher" -> onOpenTeacher(id)
                            }
                        },
                    )
                }
            }
        }
    }
}
