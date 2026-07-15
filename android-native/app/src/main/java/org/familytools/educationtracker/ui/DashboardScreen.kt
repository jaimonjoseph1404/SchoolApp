@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class ModuleCard(val icon: ImageVector, val title: String, val subtitle: String, val route: String)

private val MODULES = listOf(
    ModuleCard(Icons.Filled.Face, "Children", "Manage enrolled children", "children"),
    ModuleCard(Icons.Filled.AccountBalance, "Academic Records", "Marks & term history", "academic_records"),
    ModuleCard(Icons.Filled.CameraAlt, "Scan Report", "OCR report capture", "scan_report"),
    ModuleCard(Icons.Filled.Payments, "Expenses", "Track educational costs", "expenses"),
    ModuleCard(Icons.Filled.SupervisorAccount, "Teachers", "Teacher effectiveness", "teachers"),
    ModuleCard(Icons.Filled.ShowChart, "Analytics", "Trends & AI insights", "analytics"),
    ModuleCard(Icons.Filled.Assessment, "Reports", "PDF & CSV exports", "reports"),
    ModuleCard(Icons.Filled.CloudUpload, "Backup", "Export & restore data", "backup"),
    ModuleCard(Icons.Filled.Settings, "Settings", "Theme, PIN & biometrics", "settings"),
)

@Composable
fun DashboardScreen(childViewModel: ChildViewModel, onNavigate: (String) -> Unit, onSearch: () -> Unit) {
    val children by childViewModel.children.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Education Tracker") },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(children.size.toString(), style = MaterialTheme.typography.headlineMedium)
                    Text("Children enrolled", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("Modules", style = MaterialTheme.typography.titleMedium)

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(760.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(MODULES) { module ->
                    Card(
                        onClick = { onNavigate(module.route) },
                        colors = CardDefaults.cardColors(),
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Icon(module.icon, contentDescription = null)
                            Text(module.title, style = MaterialTheme.typography.titleSmall)
                            Text(module.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
