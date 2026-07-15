package org.familytools.educationtracker.services

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class ExtractedMarkRow(
    val subject: String,
    val marksObtained: Double?,
    val maxMarks: Double?,
    val grade: String,
    val percentage: Double?,
    val rank: Int?,
    val remarks: String,
)

data class ParsedReportCard(
    val studentName: String = "",
    val registerNo: String = "",
    val className: String = "",
    val section: String = "",
    val academicYear: String = "",
    val examType: String = "",
    val examDate: String = "",
    val attendance: String = "",
    val teacherRemarks: String = "",
    val subjectRows: List<ExtractedMarkRow> = emptyList(),
)

data class ExtractedReceipt(
    val schoolName: String = "",
    val studentName: String = "",
    val receiptNumber: String = "",
    val receiptDate: String = "",
    val totalAmount: Double? = null,
    val suggestedCategory: String = "",
)

/**
 * On-device OCR via Google ML Kit — replaces Tesseract from the Kivy build,
 * which had no working Android recipe. No native binary to bundle, and
 * ML Kit's Latin text model ships as part of the app / is fetched once by
 * Play Services, so this works fully offline after first use.
 *
 * Parsing works off the flattened recognized text (not per-block bounding
 * boxes) since ML Kit already groups OCR output into visual lines that
 * follow a photographed table's rows closely enough for regex/token
 * parsing — layout-aware column reconstruction isn't needed for the
 * report-card and receipt formats this targets.
 */
object OcrService {
    // Lazy: constructing the ML Kit client touches Android runtime classes,
    // which would otherwise blow up plain-JVM unit tests of the pure-Kotlin
    // parsing functions below the moment anything on this object is touched.
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun extractText(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        return result.text
    }

    // --- Shared field extraction -------------------------------------------------

    private val defaultStopWords = listOf("Class", "Section", "Register", "Roll", "Attendance", "Date")

    /** Captures the text following a label on the same line, stopping at a
     * double-space gap (adjacent table cell), a known next-label word, or EOL. */
    private fun captureField(text: String, labelPattern: String, stopWords: List<String> = defaultStopWords): String {
        // "." never matches '\n', so a capture naturally can't cross a line —
        // but the lookahead still needs an explicit "\n|$" branch, since bare
        // "$" only matches the end of the whole (multi-line) input, not EOL.
        val stopBranch = if (stopWords.isEmpty()) {
            ""
        } else {
            "\\s+(?:${stopWords.joinToString("|") { Regex.escape(it) }})\\b|"
        }
        val regex = Regex(
            "$labelPattern\\s*[:\\-]?\\s*(.+?)(?=\\s{2,}|$stopBranch\\n|$)",
            RegexOption.IGNORE_CASE,
        )
        return regex.find(text)?.groupValues?.get(1)?.trim(' ', ':', '-')?.trim().orEmpty()
    }

    private fun extractStudentName(text: String): String = captureField(text, "Student\\s*Name")

    // --- Progress report parsing --------------------------------------------------

    private val gradeToken = Regex("^[A-Fa-f][+-]?$")
    private val numberToken = Regex("^\\d{1,3}(?:\\.\\d+)?$")
    private val skipTrailingWords = setOf("THEORY", "PRACTICAL", "TH", "PR", "PRAC")
    private val nonSubjectLineStarts = setOf("TOTAL", "S.NO", "SNO", "PART-I", "PART-II", "PART")

    private fun parseSubjectRow(line: String): ExtractedMarkRow? {
        val raw = line.trim()
        if (raw.isEmpty()) return null
        val upperFirst = raw.uppercase()
        if (nonSubjectLineStarts.any { upperFirst.startsWith(it) }) return null

        val tokens = raw.split(Regex("\\s+")).toMutableList()
        if (tokens.size < 3) return null

        var grade = ""
        if (gradeToken.matches(tokens.last())) {
            grade = tokens.removeAt(tokens.size - 1).uppercase()
        }

        val numbers = mutableListOf<String>()
        while (tokens.isNotEmpty() && numberToken.matches(tokens.last()) && numbers.size < 4) {
            numbers.add(0, tokens.removeAt(tokens.size - 1))
        }
        if (numbers.isEmpty()) return null

        if (tokens.isNotEmpty() && tokens.first().matches(Regex("^\\d{1,2}$"))) {
            tokens.removeAt(0)
        }
        while (tokens.isNotEmpty() && tokens.last().uppercase() in skipTrailingWords) {
            tokens.removeAt(tokens.size - 1)
        }
        val subject = tokens.joinToString(" ").trim()
        if (subject.length < 2 || subject.any { it.isDigit() }) return null

        val max: Double?
        val min: Double?
        val score: Double?
        val avg: Double?
        when (numbers.size) {
            4 -> {
                max = numbers[0].toDouble(); min = numbers[1].toDouble()
                score = numbers[2].toDouble(); avg = numbers[3].toDouble()
            }
            3 -> {
                max = numbers[0].toDouble(); min = numbers[1].toDouble()
                score = numbers[2].toDouble(); avg = score
            }
            2 -> {
                score = numbers[0].toDouble(); max = numbers[1].toDouble()
                min = null; avg = null
            }
            else -> {
                score = numbers[0].toDouble(); max = null; min = null; avg = null
            }
        }
        val percentage = avg ?: if (max != null && max != 0.0 && score != null) (score / max) * 100.0 else null

        return ExtractedMarkRow(
            subject = subject,
            marksObtained = score,
            maxMarks = max,
            grade = grade,
            percentage = percentage,
            rank = null,
            remarks = if (min != null) "Min $min" else "",
        )
    }

    fun parseReportText(text: String): List<ExtractedMarkRow> =
        text.lines().mapNotNull { parseSubjectRow(it) }

    fun parseProgressReport(text: String): ParsedReportCard {
        val studentName = extractStudentName(text)
        val registerNo = captureField(text, "Regi?ster\\s*No\\.?")

        var className = ""
        var section = ""
        Regex("Class\\s*[:\\-]?\\s*([IVXLCDM0-9]{1,4})\\s*-\\s*([A-Za-z0-9]{1,3})\\b", RegexOption.IGNORE_CASE)
            .find(text)?.let {
                className = it.groupValues[1].trim()
                section = it.groupValues[2].trim()
            }
        if (className.isEmpty()) {
            Regex("Class\\s*[:\\-]?\\s*([IVXLCDM0-9]{1,4})\\b(?!\\s*Teacher)", RegexOption.IGNORE_CASE)
                .find(text)?.let { className = it.groupValues[1].trim() }
        }

        var examType = ""
        var academicYear = ""
        Regex("Progress\\s*Report\\s*[:\\-]?\\s*(.+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.let { rest ->
            val trimmed = rest.trim()
            val yearSplit = Regex("^(.*?)\\s*-\\s*(\\d{4}(?:-\\d{2,4})?)$").find(trimmed)
            if (yearSplit != null) {
                examType = yearSplit.groupValues[1].trim()
                academicYear = yearSplit.groupValues[2].trim()
            } else {
                examType = trimmed
            }
        }
        if (academicYear.isEmpty()) {
            Regex("(\\d{4}\\s*-\\s*\\d{2,4})").find(text)?.let { academicYear = it.groupValues[1].replace(" ", "") }
        }

        val attendance = Regex("Attendance\\s*[:\\-]?\\s*(\\d+\\s*/\\s*\\d+)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.replace(" ", "").orEmpty()

        var teacherRemarks = captureField(text, "Teacher'?s?\\s*Remarks?", stopWords = emptyList())
        if (teacherRemarks.isEmpty()) {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val idx = lines.indexOfFirst { Regex("Teacher'?s?\\s*Remarks?", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            if (idx in 0 until lines.size - 1) teacherRemarks = lines[idx + 1]
        }

        val subjectRows = parseReportText(text)

        return ParsedReportCard(
            studentName = studentName,
            registerNo = registerNo,
            className = className,
            section = section,
            academicYear = academicYear,
            examType = examType,
            attendance = attendance,
            teacherRemarks = teacherRemarks,
            subjectRows = subjectRows,
        )
    }

    // --- Fee receipt parsing -------------------------------------------------------

    private val receiptNumberRegex = Regex("(?:receipt|invoice)\\s*(?:no\\.?|number|#)\\s*[:\\-]?\\s*([A-Za-z0-9\\-/]+)", RegexOption.IGNORE_CASE)
    private val receiptDateRegex = Regex("date\\s*[:\\-]?\\s*([0-3]?\\d[/-][01]?\\d[/-]\\d{2,4})", RegexOption.IGNORE_CASE)
    private val totalAmountRegex = Regex("(?:total|grand total|amount payable|net amount)\\s*[:\\-]?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)

    private val categoryKeywords = linkedMapOf(
        "TUITION" to "Tuition Fee",
        "ADMISSION" to "Admission Fee",
        "BOOK" to "Books",
        "UNIFORM" to "Uniform",
        "TRANSPORT" to "Bus Fee",
        "BUS" to "Bus Fee",
        "EXAM" to "Examination Fee",
        "ACTIVITY" to "Activity Fee",
        "SPORT" to "Sports Fee",
        "LAB" to "Laboratory Fee",
        "COMPUTER" to "Technology Fee",
        "TECHNOLOGY" to "Technology Fee",
        "COACHING" to "Coaching Fee",
    )

    private fun guessCategory(text: String): String {
        val upper = text.uppercase()
        for ((keyword, category) in categoryKeywords) {
            if (upper.contains(keyword)) return category
        }
        return "Miscellaneous"
    }

    fun parseReceiptText(text: String): ExtractedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val schoolName = lines.firstOrNull()?.take(120).orEmpty()
        val studentName = extractStudentName(text)
        val receiptNumber = receiptNumberRegex.find(text)?.groupValues?.get(1).orEmpty()
        val receiptDate = receiptDateRegex.find(text)?.groupValues?.get(1).orEmpty()
        val totalAmount = totalAmountRegex.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val suggestedCategory = guessCategory(text)
        return ExtractedReceipt(schoolName, studentName, receiptNumber, receiptDate, totalAmount, suggestedCategory)
    }
}
