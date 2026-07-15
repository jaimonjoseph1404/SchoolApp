package org.familytools.educationtracker.services

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import org.familytools.educationtracker.data.ExpenseRow
import org.familytools.educationtracker.data.MarkHistoryRow
import org.familytools.educationtracker.data.TeacherEffectivenessRow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** PDF via android.graphics.pdf.PdfDocument — built into the platform, no
 * library needed (unlike reportlab on the Kivy build, which had no Android
 * recipe and crashed the whole app when its import failed). */
object ReportService {
    private fun timestampedName(prefix: String, ext: String): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_$ts.$ext"
    }

    private fun reportsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "reports").apply { mkdirs() }

    fun academicSummaryPdf(context: Context, childName: String, rows: List<MarkHistoryRow>): File {
        val file = File(reportsDir(context), timestampedName("academic_summary_$childName", "pdf"))
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 at 72dpi
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val headerPaint = Paint().apply { textSize = 10f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 9f }

        var y = 40f
        canvas.drawText("Academic Summary — $childName", 24f, y, titlePaint)
        y += 30f

        val columns = listOf("Year", "Class", "Term", "Exam", "Subject", "Marks", "%", "Grade")
        val colX = listOf(24f, 100f, 170f, 230f, 300f, 400f, 450f, 500f)
        columns.forEachIndexed { i, col -> canvas.drawText(col, colX[i], y, headerPaint) }
        y += 16f

        if (rows.isEmpty()) {
            canvas.drawText("No records yet", 24f, y, bodyPaint)
        } else {
            for (r in rows) {
                if (y > 800f) {
                    document.finishPage(page)
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = 40f
                }
                val marks = if (r.marksObtained != null && r.maxMarks != null) {
                    "${r.marksObtained.toInt()}/${r.maxMarks.toInt()}"
                } else "-"
                val pct = r.percentage?.let { "%.1f".format(it) } ?: "-"
                val values = listOf(r.yearLabel, r.className, r.termName, r.examType, r.subjectName, marks, pct, r.grade.ifBlank { "-" })
                values.forEachIndexed { i, v -> canvas.drawText(v, colX[i], y, bodyPaint) }
                y += 14f
            }
        }
        document.finishPage(page)
        document.writeTo(file.outputStream())
        document.close()
        return file
    }

    fun teacherEffectivenessPdf(context: Context, teacherName: String, rows: List<TeacherEffectivenessRow>): File {
        val file = File(reportsDir(context), timestampedName("teacher_effectiveness_$teacherName", "pdf"))
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val headerPaint = Paint().apply { textSize = 10f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 9f }

        var y = 40f
        canvas.drawText("Teacher Effectiveness — $teacherName", 24f, y, titlePaint)
        y += 30f
        canvas.drawText("Academic Year", 24f, y, headerPaint)
        canvas.drawText("Average %", 200f, y, headerPaint)
        canvas.drawText("Marks Recorded", 320f, y, headerPaint)
        y += 16f

        if (rows.isEmpty()) {
            canvas.drawText("No records yet", 24f, y, bodyPaint)
        } else {
            for (r in rows) {
                canvas.drawText(r.yearLabel, 24f, y, bodyPaint)
                canvas.drawText(r.avgPercentage?.let { "%.1f".format(it) } ?: "-", 200f, y, bodyPaint)
                canvas.drawText(r.markCount.toString(), 320f, y, bodyPaint)
                y += 14f
            }
        }
        document.finishPage(page)
        document.writeTo(file.outputStream())
        document.close()
        return file
    }

    fun expenseReportCsv(context: Context, childName: String, rows: List<ExpenseRow>): File {
        val file = File(reportsDir(context), timestampedName("expenses_$childName", "csv"))
        file.bufferedWriter().use { writer ->
            writer.write("Date,Category,Academic Year,Amount,Description\n")
            for (e in rows) {
                val line = listOf(e.expenseDate, e.categoryName, e.yearLabel ?: "", e.amount.toString(), e.description)
                    .joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
                writer.write("$line\n")
            }
        }
        return file
    }
}
