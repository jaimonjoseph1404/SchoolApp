@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.familytools.educationtracker.data.Child

@Composable
fun ChildListScreen(
    childViewModel: ChildViewModel,
    onBack: () -> Unit,
    onAddChild: () -> Unit,
    onEditChild: (Child) -> Unit,
) {
    val children by childViewModel.children.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Children") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddChild) {
                Icon(Icons.Filled.Add, contentDescription = "Add child")
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (children.isEmpty()) {
                Text(
                    "No children added yet. Tap + to add one.",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn {
                    items(children, key = { it.id }) { child ->
                        val subtitle = listOf(child.currentClass, child.schoolName)
                            .filter { it.isNotBlank() }
                            .joinToString(" | ")
                        ListItem(
                            headlineContent = { Text(child.fullName.ifBlank { "Unnamed" }) },
                            supportingContent = { Text(subtitle.ifBlank { "No class/school set" }) },
                            leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                            modifier = Modifier
                                .clickable { onEditChild(child) }
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
