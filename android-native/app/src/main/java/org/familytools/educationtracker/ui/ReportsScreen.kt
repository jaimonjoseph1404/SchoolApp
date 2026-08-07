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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ReportsScreen(viewModel: ReportsViewModel, onBack: () -> Unit) {
    val children by viewModel.children.collectAsState()
    val teachers by viewModel.teachers.collectAsState()
    val academicPreview by viewModel.academicPreview.collectAsState()
    val expensePreview by viewModel.expensePreview.collectAsState()
    val teacherPreview by viewModel.teacherPreview.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedChild by remember { mutableStateOf<org.familytools.educationtracker.data.Child?>(null) }
    var selectedTeacher by remember { mutableStateOf<org.familytools.educationtracker.data.Teacher?>(null) }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Academic & Expense Reports", style = MaterialTheme.typography.titleMedium)
            EntityDropdownField(
                "Child", children, selectedChild?.fullName ?: "", { it.fullName },
                { selectedChild = it; viewModel.loadChildPreview(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val child = selectedChild ?: return@Button notify("Select a child first")
                    viewModel.generateAcademicPdf(context, child.id, child.fullName) { notify("Saved: ${it.name}") }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Academic Year Summary (PDF)") }
            Button(
                onClick = {
                    val child = selectedChild ?: return@Button notify("Select a child first")
                    viewModel.generateExpenseCsv(context, child.id, child.fullName) { notify("Saved: ${it.name}") }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Expense Report (CSV)") }

            if (selectedChild != null) {
                Text("Marks — ${selectedChild!!.fullName}", style = MaterialTheme.typography.titleSmall)
                if (academicPreview.isEmpty()) {
                    Text("No academic records yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Performance Insights", style = MaterialTheme.typography.titleSmall)
                    viewModel.performanceInsights(academicPreview).forEach {
                        Text("•  $it", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    MarksChartsAndAnalysis(viewModel, academicPreview, selectedChild!!.id)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("All Records", style = MaterialTheme.typography.titleSmall)
                    academicPreview.forEach { row ->
                        Text(
                            "${row.yearLabel} · ${row.className} · ${row.termName} · ${row.examType} — " +
                                "${row.subjectName}: ${row.marksObtained?.toInt() ?: "-"}/${row.maxMarks?.toInt() ?: "-"} " +
                                "(${row.grade.ifBlank { "-" }})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Expenses — ${selectedChild!!.fullName}", style = MaterialTheme.typography.titleSmall)
                if (expensePreview.isEmpty()) {
                    Text("No expenses recorded yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    expensePreview.forEach { row ->
                        Text(
                            // categoryName is free-typed — format only the numeric part,
                            // then concatenate, so a literal "%" in the category name
                            // can't break String.format's template parsing.
                            "${row.expenseDate.ifBlank { "-" }} · ${row.categoryName} — Rs. " + "%,.2f".format(row.amount),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            Text("Teacher Effectiveness Report", style = MaterialTheme.typography.titleMedium)
            EntityDropdownField(
                "Teacher", teachers, selectedTeacher?.name ?: "", { it.name },
                { selectedTeacher = it; viewModel.loadTeacherPreview(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val teacher = selectedTeacher ?: return@Button notify("Select a teacher first")
                    viewModel.generateTeacherPdf(context, teacher.id, teacher.name) { notify("Saved: ${it.name}") }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Teacher Effectiveness (PDF)") }

            if (selectedTeacher != null) {
                Text("Effectiveness — ${selectedTeacher!!.name}", style = MaterialTheme.typography.titleSmall)
                if (teacherPreview.isEmpty()) {
                    Text("No assigned marks yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    teacherPreview.forEach { row ->
                        Text(
                            "${row.yearLabel}: avg ${row.avgPercentage?.let { "%.1f%%".format(it) } ?: "-"} " +
                                "across ${row.markCount} mark(s)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Text(
                "PDF/CSV exports are saved to the app's private external storage folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Bar-chart view of overall + subject-wise marks (percentage or actual,
 * toggle-able), scoped to all-time or one academic year; top-performing /
 * needs-attention subjects; and a term+subject drill-down showing exactly
 * one exam's mark for one subject plus how it compares to that subject's
 * overall average. Pulled out of [ReportsScreen] itself only because it has
 * enough of its own local UI state (scope, value mode, drill-down picks) to
 * read poorly inline. */
@Composable
private fun MarksChartsAndAnalysis(
    viewModel: ReportsViewModel,
    rows: List<org.familytools.educationtracker.data.MarkHistoryRow>,
    childId: Long,
) {
    val engine = viewModel.engine
    val years = rows.map { it.yearLabel }.filter { it.isNotBlank() }.distinct().sortedDescending()
    val allYearsLabel = "All Years (since 1st scanned record)"

    var scopeYear by remember(childId) { mutableStateOf(allYearsLabel) }
    var showActual by remember(childId) { mutableStateOf(false) }
    var chartSubject by remember(childId) { mutableStateOf("") }
    var drillExam by remember(childId) { mutableStateOf("") }
    var drillSubject by remember(childId) { mutableStateOf("") }

    Text("Charts & Analysis", style = MaterialTheme.typography.titleMedium)
    EntityDropdownField(
        "Scope", listOf(allYearsLabel) + years, scopeYear, { it }, { scopeYear = it },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !showActual, onClick = { showActual = false }, label = { Text("Percentage") })
        FilterChip(selected = showActual, onClick = { showActual = true }, label = { Text("Actual Marks") })
    }

    val scopedRows = if (scopeYear == allYearsLabel) rows else rows.filter { it.yearLabel == scopeYear }
    fun barsFor(aggregates: List<org.familytools.educationtracker.services.ExamAggregate>) = aggregates.map { agg ->
        val display = if (showActual) "${agg.obtained.toInt()}/${agg.max.toInt()}" else "%.0f%%".format(agg.percentage)
        BarItem(agg.label, agg.percentage, display)
    }

    BarChartView(barsFor(engine.overallExamAggregates(scopedRows)), "Overall Marks by Term")

    val subjectNames = scopedRows.map { it.subjectName }.distinct().sorted()
    if (subjectNames.isNotEmpty()) {
        EntityDropdownField(
            "Subject Chart", subjectNames, chartSubject.ifBlank { "Select a subject" }, { it }, { chartSubject = it },
            modifier = Modifier.fillMaxWidth(),
        )
        if (chartSubject.isNotBlank()) {
            BarChartView(barsFor(engine.subjectExamAggregates(scopedRows, chartSubject)), "$chartSubject by Term")
        }
    }

    val (strengths, weaknesses) = engine.strengthsAndWeaknesses(scopedRows)
    Text("Top Performing Subjects", style = MaterialTheme.typography.titleSmall)
    if (strengths.isEmpty()) {
        Text("Not enough data yet.", style = MaterialTheme.typography.bodySmall)
    } else {
        // subject is free text — format the number alone, then concatenate
        // (see LineChartView's valueLabel comment in Charts.kt for why).
        strengths.forEach { (subject, avg) -> Text("•  $subject — " + "%.0f%%".format(avg), style = MaterialTheme.typography.bodySmall) }
    }
    Text("Needs Attention", style = MaterialTheme.typography.titleSmall)
    if (weaknesses.isEmpty()) {
        Text("Nothing flagged in this scope.", style = MaterialTheme.typography.bodySmall)
    } else {
        weaknesses.forEach { (subject, avg) -> Text("•  $subject — " + "%.0f%%".format(avg), style = MaterialTheme.typography.bodySmall) }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Text("Term & Subject Analysis", style = MaterialTheme.typography.titleMedium)
    val examLabels = engine.availableExams(scopedRows)
    if (examLabels.isNotEmpty() && subjectNames.isNotEmpty()) {
        EntityDropdownField(
            "Term / Exam", examLabels, drillExam.ifBlank { "Select a term/exam" }, { it }, { drillExam = it },
            modifier = Modifier.fillMaxWidth(),
        )
        EntityDropdownField(
            "Subject", subjectNames, drillSubject.ifBlank { "Select a subject" }, { it }, { drillSubject = it },
            modifier = Modifier.fillMaxWidth(),
        )
        if (drillExam.isNotBlank() && drillSubject.isNotBlank()) {
            val mark = engine.findMark(scopedRows, drillExam, drillSubject)
            if (mark == null) {
                Text("No record for $drillSubject in $drillExam.", style = MaterialTheme.typography.bodySmall)
            } else {
                val subjectAvg = engine.subjectAverages(scopedRows)[drillSubject]
                // drillSubject is free text — every branch below builds its
                // number(s) first, then concatenates the subject name in.
                val compareText = if (subjectAvg != null && mark.percentage != null) {
                    val diff = mark.percentage - subjectAvg
                    val avgText = "%.0f%%".format(subjectAvg)
                    when {
                        abs(diff) < 1.0 -> "right at $drillSubject's overall average ($avgText) for this scope"
                        diff > 0 -> "%.0f".format(diff) + " points above $drillSubject's overall average ($avgText) for this scope"
                        else -> "%.0f".format(-diff) + " points below $drillSubject's overall average ($avgText) for this scope"
                    }
                } else {
                    null
                }
                Text(
                    "Score: ${mark.marksObtained?.toInt() ?: "-"}/${mark.maxMarks?.toInt() ?: "-"} " +
                        "(${mark.grade.ifBlank { "-" }})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (compareText != null) Text("That's $compareText.", style = MaterialTheme.typography.bodySmall)
                if (mark.teacherRemarks.isNotBlank()) {
                    Text("Teacher's Remarks: ${mark.teacherRemarks}", style = MaterialTheme.typography.bodySmall)
                }
                if (mark.remarks.isNotBlank()) Text("Notes: ${mark.remarks}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
