package org.familytools.educationtracker.services

import org.familytools.educationtracker.data.AcademicDao
import org.familytools.educationtracker.data.ExpenseDao
import org.familytools.educationtracker.data.MarkHistoryRow
import kotlin.math.max
import kotlin.math.min

data class TrendPoint(val label: String, val value: Double)

/** One exam's marks, in both forms — [percentage] drives chart height
 * uniformly, [obtained]/[max] are the actual marks for display when the
 * user wants "actual marks" rather than percentage. */
data class ExamAggregate(val label: String, val obtained: Double, val max: Double, val percentage: Double)

data class Prediction(
    val predictedValue: Double?,
    val confidence: Double?,
    val trend: String,
    val slope: Double?,
)

private fun linearFit(values: List<Double>): Prediction {
    val n = values.size
    if (n < 2) {
        return Prediction(values.firstOrNull(), null, "insufficient data", null)
    }
    val xs = (0 until n).map { it.toDouble() }
    val xMean = xs.average()
    val yMean = values.average()
    val ssXy = xs.indices.sumOf { (xs[it] - xMean) * (values[it] - yMean) }
    val ssXx = xs.sumOf { (it - xMean) * (it - xMean) }
    val slope = if (ssXx != 0.0) ssXy / ssXx else 0.0
    val intercept = yMean - slope * xMean
    val predicted = slope * n + intercept

    val ssRes = xs.indices.sumOf { val yHat = slope * xs[it] + intercept; (values[it] - yHat) * (values[it] - yHat) }
    val ssTot = values.sumOf { (it - yMean) * (it - yMean) }
    val r2 = if (ssTot > 0) max(0.0, min(1.0, 1 - ssRes / ssTot)) else 0.0

    val trend = when {
        kotlin.math.abs(slope) < 0.5 -> "stable"
        slope > 0 -> "improving"
        else -> "declining"
    }
    return Prediction(predicted, r2, trend, slope)
}

class AnalyticsEngine(private val academicDao: AcademicDao, private val expenseDao: ExpenseDao) {

    private fun examGroups(rows: List<MarkHistoryRow>): LinkedHashMap<String, MutableList<MarkHistoryRow>> {
        val groups = LinkedHashMap<String, MutableList<MarkHistoryRow>>()
        for (r in rows) {
            val key = "${r.yearLabel} ${r.termName} ${r.examType}"
            groups.getOrPut(key) { mutableListOf() }.add(r)
        }
        return groups
    }

    /** Every distinct exam (year + term + exam type) present in [rows], in
     * first-seen order — the same labels used by every chart/trend below,
     * so a picked label always lines up with what's plotted. */
    fun availableExams(rows: List<MarkHistoryRow>): List<String> = examGroups(rows).keys.toList()

    /** The specific mark for one subject within one exam — the entry point
     * for a "this term, this subject" drill-down (score/grade/remarks for
     * exactly the one exam+subject combination picked, not an aggregate). */
    fun findMark(rows: List<MarkHistoryRow>, examLabel: String, subject: String): MarkHistoryRow? =
        examGroups(rows)[examLabel]?.firstOrNull { it.subjectName == subject }

    /** Overall marks per exam, summed across every subject that has both a
     * score and a max — the "actual marks" counterpart to [percentageTrend],
     * which averages percentages instead. */
    fun overallExamAggregates(rows: List<MarkHistoryRow>): List<ExamAggregate> =
        examGroups(rows).mapNotNull { (label, group) ->
            val withMarks = group.filter { it.marksObtained != null && it.maxMarks != null }
            if (withMarks.isEmpty()) return@mapNotNull null
            val obtained = withMarks.sumOf { it.marksObtained!! }
            val max = withMarks.sumOf { it.maxMarks!! }
            ExamAggregate(label, obtained, max, if (max > 0) obtained / max * 100 else 0.0)
        }

    /** One subject's marks per exam, in both actual and percentage form. */
    fun subjectExamAggregates(rows: List<MarkHistoryRow>, subject: String): List<ExamAggregate> =
        examGroups(rows).mapNotNull { (label, group) ->
            val row = group.firstOrNull { it.subjectName == subject && it.marksObtained != null && it.maxMarks != null }
                ?: return@mapNotNull null
            val max = row.maxMarks!!
            val obtained = row.marksObtained!!
            ExamAggregate(label, obtained, max, row.percentage ?: (if (max > 0) obtained / max * 100 else 0.0))
        }

    fun percentageTrend(rows: List<MarkHistoryRow>): List<TrendPoint> =
        examGroups(rows).mapNotNull { (label, group) ->
            val pcts = group.mapNotNull { it.percentage }
            if (pcts.isEmpty()) null else TrendPoint(label, pcts.average())
        }

    fun subjectTrend(rows: List<MarkHistoryRow>, subject: String): List<TrendPoint> =
        examGroups(rows).entries.flatMap { (label, group) ->
            group.filter { it.subjectName == subject && it.percentage != null }
                .map { TrendPoint(label, it.percentage!!) }
        }

    fun subjectAverages(rows: List<MarkHistoryRow>): Map<String, Double> =
        rows.filter { it.percentage != null }
            .groupBy { it.subjectName }
            .mapValues { (_, v) -> v.map { it.percentage!! }.average() }

    fun predictOverall(rows: List<MarkHistoryRow>): Prediction = linearFit(percentageTrend(rows).map { it.value })

    fun predictSubject(rows: List<MarkHistoryRow>, subject: String): Prediction =
        linearFit(subjectTrend(rows, subject).map { it.value })

    fun strengthsAndWeaknesses(rows: List<MarkHistoryRow>, topN: Int = 3): Pair<List<Pair<String, Double>>, List<Pair<String, Double>>> {
        val ranked = subjectAverages(rows).entries.sortedByDescending { it.value }.map { it.key to it.value }
        val strengths = ranked.take(topN)
        val weaknesses = if (ranked.size > topN) ranked.takeLast(topN).reversed() else emptyList()
        return strengths to weaknesses
    }

    fun generateInsights(rows: List<MarkHistoryRow>): List<String> {
        val insights = mutableListOf<String>()
        val overall = predictOverall(rows)
        if (overall.trend == "improving" && overall.slope != null) {
            insights.add("Overall performance improved by roughly %.1f points per exam.".format(overall.slope))
        } else if (overall.trend == "declining" && overall.slope != null) {
            insights.add("Overall performance is declining by roughly %.1f points per exam.".format(kotlin.math.abs(overall.slope)))
        }

        // subject is free text (OCR/AI-extracted or user-typed) — every line
        // below formats its own number first, then concatenates the subject
        // name in, rather than interpolating it into the format template.
        // A subject name that ever contains a literal "%" (a real OCR
        // failure mode: a stray percentage-column character bleeding into
        // the name) would otherwise crash String.format on a dangling "%"
        // (reproduced and fixed once already for the confidence-percentage
        // string in AnalyticsScreen — same root cause).
        for (subject in subjectAverages(rows).keys.sorted()) {
            val pred = predictSubject(rows, subject)
            if (pred.trend == "improving" && pred.slope != null) {
                insights.add("$subject scores are trending upward (+" + "%.1f".format(pred.slope) + " pts/exam).")
            } else if (pred.trend == "declining" && pred.slope != null) {
                insights.add(
                    "$subject scores are declining (" + "%.1f".format(pred.slope) + " pts/exam). Additional support may help.",
                )
            }
        }

        val (strengths, weaknesses) = strengthsAndWeaknesses(rows)
        strengths.firstOrNull()?.let { (subject, avg) ->
            insights.add("$subject is a strength area, averaging " + "%.0f%%.".format(avg))
        }
        weaknesses.firstOrNull()?.let { (subject, avg) ->
            insights.add("$subject is the weakest area, averaging " + "%.0f%%.".format(avg))
        }

        if (insights.isEmpty()) insights.add("Add more exam records to unlock trend insights.")
        return insights
    }

    /** Concrete, actionable suggestions (not just observations) for subjects
     * that are either weak (below 50% average) or trending downward —
     * what a parent should actually consider doing next, not just a chart
     * of what already happened. */
    fun improvementActions(rows: List<MarkHistoryRow>): List<String> {
        val actions = mutableListOf<String>()
        val averages = subjectAverages(rows)
        for (subject in averages.keys.sorted()) {
            val avg = averages.getValue(subject)
            val declining = predictSubject(rows, subject).trend == "declining"
            val weak = avg < 50.0
            when {
                declining && weak -> actions.add(
                    "$subject: low (" + "%.0f%%".format(avg) +
                        " avg) and declining — prioritize this subject with focused daily practice or a tutor.",
                )
                declining -> actions.add(
                    "$subject: trending down — review recent test papers together to catch the specific topics slipping.",
                )
                weak -> actions.add(
                    "$subject: averaging " + "%.0f%%".format(avg) + " — targeted revision before the next exam could help.",
                )
            }
        }
        if (actions.isEmpty() && averages.isNotEmpty()) {
            actions.add("No subject currently needs urgent attention — keep up the consistent revision routine.")
        }
        return actions
    }

    fun subjectRiskScores(rows: List<MarkHistoryRow>): Map<String, Double> =
        subjectAverages(rows).mapValues { (subject, avg) ->
            val pred = predictSubject(rows, subject)
            val slopePenalty = max(0.0, -(pred.slope ?: 0.0)) / 10.0
            val lowScorePenalty = max(0.0, (60 - avg) / 60.0)
            min(1.0, slopePenalty + lowScorePenalty)
        }

    suspend fun expenseYearlyGrowth(childId: Long): Prediction {
        val values = expenseDao.totalByYear(childId).filter { it.yearLabel != "Unassigned" }.map { it.total }
        return linearFit(values)
    }

    suspend fun expenseAverageGrowthRate(childId: Long): Double? {
        val values = expenseDao.totalByYear(childId)
            .filter { it.yearLabel != "Unassigned" && it.total > 0 }
            .map { it.total }
        if (values.size < 2) return null
        val rates = values.zipWithNext { prev, cur -> if (prev != 0.0) (cur - prev) / prev else null }.filterNotNull()
        return if (rates.isEmpty()) null else rates.average() * 100
    }
}
