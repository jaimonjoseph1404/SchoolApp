@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.familytools.educationtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel, onBack: () -> Unit) {
    val children by viewModel.children.collectAsState()
    val rows by viewModel.marksHistory.collectAsState()
    val childId by viewModel.selectedChildId.collectAsState()
    var selectedChildName by remember { mutableStateOf("") }

    val engine = viewModel.engine
    val trend = engine.percentageTrend(rows)
    val averages = engine.subjectAverages(rows)
    val overall = engine.predictOverall(rows)
    val insights = engine.generateInsights(rows)
    val riskScores = engine.subjectRiskScores(rows)

    val growth by produceState(initialValue = null as org.familytools.educationtracker.services.Prediction?, childId) {
        value = childId?.let { engine.expenseYearlyGrowth(it) }
    }
    val avgGrowthRate by produceState(initialValue = null as Double?, childId) {
        value = childId?.let { engine.expenseAverageGrowthRate(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EntityDropdownField(
                "Child", children, selectedChildName, { it.fullName },
                { selectedChildName = it.fullName; viewModel.selectChild(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (childId != null) {
                LineChartView(trend, "Overall Percentage Trend")
                RadarChartView(averages, "Subject Strengths")

                Text("Predictions", style = MaterialTheme.typography.titleMedium)
                val lines = buildList {
                    if (overall.predictedValue != null) {
                        val conf = overall.confidence?.let { " (confidence %.0f%%)".format(it * 100) } ?: ""
                        add("Expected next exam average: %.1f%%$conf".format(overall.predictedValue))
                        add("Trend: ${overall.trend}")
                    } else {
                        add("Add more exams to unlock score predictions.")
                    }
                    growth?.predictedValue?.let { add("Projected next year's expenses: Rs. %,.0f".format(it)) }
                    avgGrowthRate?.let { add("Average yearly cost growth: %.1f%%".format(it)) }
                    val highRisk = riskScores.filter { it.value >= 0.5 }.keys
                    if (highRisk.isNotEmpty()) add("Subject risk score — needs attention: ${highRisk.joinToString(", ")}")
                }
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }

                Text("AI Insights", style = MaterialTheme.typography.titleMedium)
                insights.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
