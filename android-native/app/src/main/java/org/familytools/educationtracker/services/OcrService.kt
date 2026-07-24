package org.familytools.educationtracker.services

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

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
    val schoolName: String = "",
    val schoolAddress: String = "",
    val className: String = "",
    val section: String = "",
    val academicYear: String = "",
    val examType: String = "",
    val examDate: String = "",
    val attendanceDaysPresent: Int? = null,
    val attendanceWorkingDays: Int? = null,
    val teacherRemarks: String = "",
    val subjectRows: List<ExtractedMarkRow> = emptyList(),
    val coCurricularRows: List<ExtractedMarkRow> = emptyList(),
)

data class ExtractedReceipt(
    val schoolName: String = "",
    val studentName: String = "",
    val receiptNumber: String = "",
    val receiptDate: String = "",
    val totalAmount: Double? = null,
    val suggestedCategory: String = "",
)

/** Raw OCR output: [fullText] is ML Kit's own line grouping (used for
 * one-line header fields like "Student Name ..."); [rows] are lines
 * reconstructed by Y-coordinate proximity across the whole page, which is
 * far more reliable for table rows — ML Kit frequently splits a single
 * visual table row into multiple text lines when column gaps are wide
 * (very common in photographed report cards), which silently defeated the
 * old line-by-line parser. [fullText] is also surfaced in the UI so a
 * user (or a future debugging session) can see exactly what was read when
 * parsing doesn't match what's on the page. */
data class OcrResult(val fullText: String, val rows: List<String>)

/**
 * On-device OCR via Google ML Kit — replaces Tesseract from the Kivy build,
 * which had no working Android recipe. No native binary to bundle, and
 * ML Kit's Latin text model ships as part of the app / is fetched once by
 * Play Services, so this works fully offline after first use.
 */
object OcrService {
    // Lazy: constructing the ML Kit client touches Android runtime classes,
    // which would otherwise blow up plain-JVM unit tests of the pure-Kotlin
    // parsing functions below the moment anything on this object is touched.
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(context: Context, uri: Uri): OcrResult {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        return OcrResult(fullText = result.text, rows = reconstructRows(result))
    }

    suspend fun extractText(context: Context, uri: Uri): String = recognize(context, uri).fullText

    /** Groups ML Kit's recognized lines into visual rows by vertical-center
     * proximity (within ~60% of the row's average line height), then sorts
     * each group left-to-right — reconstructing table rows regardless of
     * how ML Kit itself chose to split the page into text lines. */
    private fun reconstructRows(text: Text): List<String> {
        data class LineBox(val content: String, val top: Int, val bottom: Int, val left: Int)

        val lines = text.textBlocks.flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                LineBox(line.text, box.top, box.bottom, box.left)
            }
            .sortedBy { it.top }
        if (lines.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<LineBox>>()
        for (line in lines) {
            val lineCenter = (line.top + line.bottom) / 2.0
            val targetRow = rows.lastOrNull()?.takeIf { candidate ->
                val rowCenter = candidate.sumOf { (it.top + it.bottom) / 2.0 } / candidate.size
                val avgHeight = candidate.sumOf { it.bottom - it.top } / candidate.size
                abs(lineCenter - rowCenter) <= avgHeight * 0.6
            }
            if (targetRow != null) targetRow.add(line) else rows.add(mutableListOf(line))
        }
        return rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.content } }
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

    private val studentNameLabel =
        "(?:Student\\s*Name|Name\\s*of\\s*(?:the\\s*)?(?:Student|Candidate|Pupil)|Candidate\\s*Name|Pupil'?s?\\s*Name)"

    private fun extractStudentName(text: String): String = captureField(text, studentNameLabel)

    // --- Progress report parsing --------------------------------------------------

    // CBSE-style grades (A1, B2, ...) as well as plain letter grades (A, C+, F, ...).
    private val gradeToken = Regex("^[A-Fa-f][12+-]?$")
    // "AB" (Absent) appears in place of the grade, and often in place of the
    // score/average too, on real report cards for a missed exam — a student
    // being absent for one subject shouldn't drop that subject's whole row.
    private val absentToken = Regex("^AB$", RegexOption.IGNORE_CASE)
    private val numberToken = Regex("^\\d{1,3}(?:\\.\\d+)?$")
    private val cellToken = Regex("^(?:\\d{1,3}(?:\\.\\d+)?|AB)$", RegexOption.IGNORE_CASE)
    private val skipTrailingWords = setOf("THEORY", "PRACTICAL", "TH", "PR", "PRAC")
    private val nonSubjectLineStarts = setOf("TOTAL", "S.NO", "SNO", "PART-I", "PART-II", "PART")

    /** Strips characters real photographed-table OCR frequently injects
     * around cell contents (border pipes, stray punctuation) without
     * touching the letters/digits/spaces parsing depends on. */
    private fun cleanRow(line: String): String =
        line.replace('|', ' ').replace('¦', ' ').replace(Regex("[_~`]"), " ").trim()

    private fun parseSubjectRow(rawLine: String): ExtractedMarkRow? {
        val raw = cleanRow(rawLine)
        if (raw.isEmpty()) return null
        val upperFirst = raw.uppercase()
        if (nonSubjectLineStarts.any { upperFirst.startsWith(it) }) return null

        val tokens = raw.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        if (tokens.size < 3) return null

        var grade = ""
        if (gradeToken.matches(tokens.last())) {
            grade = tokens.removeAt(tokens.size - 1).uppercase()
        } else if (absentToken.matches(tokens.last())) {
            grade = tokens.removeAt(tokens.size - 1).uppercase()
        }

        // Score/Avg cells can also read "AB" (absent) instead of a number —
        // kept in the walk (as null once converted below) so the row isn't
        // dropped just because one term's exam was missed.
        val numbers = mutableListOf<String>()
        while (tokens.isNotEmpty() && cellToken.matches(tokens.last()) && numbers.size < 4) {
            numbers.add(0, tokens.removeAt(tokens.size - 1))
        }
        if (numbers.isEmpty()) return null
        if (grade.isEmpty() && numbers.all { absentToken.matches(it) }) grade = "AB"

        if (tokens.isNotEmpty() && tokens.first().matches(Regex("^\\d{1,2}[.)]?$"))) {
            tokens.removeAt(0)
        }
        while (tokens.isNotEmpty() && tokens.last().uppercase().trim('.') in skipTrailingWords) {
            tokens.removeAt(tokens.size - 1)
        }
        val subject = tokens.joinToString(" ").trim()
        // Reject only genuine junk (no letters at all) — a stray OCR'd digit
        // inside an otherwise valid subject name shouldn't drop the whole row.
        if (subject.length < 2 || subject.none { it.isLetter() }) return null

        // "AB" cells become null here rather than crashing toDouble() — an
        // absent student still has a Max/Min column (or none, if the whole
        // row is "AB"), never a numeric score.
        val values = numbers.map { it.toDoubleOrNull() }
        val max: Double?
        val min: Double?
        val score: Double?
        val avg: Double?
        when (values.size) {
            4 -> {
                max = values[0]; min = values[1]
                score = values[2]; avg = values[3]
            }
            3 -> {
                max = values[0]; min = values[1]
                score = values[2]; avg = score
            }
            2 -> {
                score = values[0]; max = values[1]
                min = null; avg = null
            }
            else -> {
                score = values[0]; max = null; min = null; avg = null
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
            remarks = listOfNotNull(
                if (min != null) "Min $min" else null,
                if (grade == "AB") "Absent" else null,
            ).joinToString("; "),
        )
    }

    fun parseReportText(text: String): List<ExtractedMarkRow> =
        text.lines().mapNotNull { parseSubjectRow(it) }

    // Roman numerals I-XII, longest-first so alternation can't match a
    // prefix (e.g. "I" inside "III") before trying the full token.
    private const val ROMAN_CLASS = "VIII|VII|XII|III|XI|IV|IX|VI|II|X|V|I"
    private val tableStartMarker = Regex("S\\.?\\s*No\\b|Part\\s*-?\\s*I\\b", RegexOption.IGNORE_CASE)
    private val partTwoStartMarker = Regex("PART\\s*-?\\s*II\\b", RegexOption.IGNORE_CASE)

    /** Text before the marks table starts (before "S.No"/"Part-I") — header
     * fields (name, class, exam title) live here. Scoping to just this
     * region stops those fields from accidentally matching unrelated
     * numbers/words deeper in the marks table or the co-curricular section
     * (e.g. an attendance count getting picked up as the class number). */
    private fun headerText(text: String): String {
        val end = tableStartMarker.find(text)?.range?.first ?: text.length
        return text.substring(0, end)
    }

    /** [layoutRows], when supplied by [recognize], reconstructs table rows
     * from bounding boxes rather than trusting ML Kit's own line breaks —
     * pass [OcrResult.rows] here for real scans. Defaults to a plain line
     * split so pure-text callers (tests, previously-extracted text) still
     * work without layout information. */
    fun parseProgressReport(text: String, layoutRows: List<String> = text.lines()): ParsedReportCard {
        val header = headerText(text)
        val studentName = extractStudentName(header)
        val registerNo = captureField(header, "Regi?ster\\s*No\\.?")

        val preHeaderLines = header.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .takeWhile { !Regex("Progress\\s*Report", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        val schoolName = preHeaderLines.firstOrNull { it.count { c -> c.isLetter() } >= 4 }.orEmpty()
        val schoolAddress = preHeaderLines.filter { it != schoolName }.joinToString(", ")

        var className = ""
        var section = ""
        Regex(
            "Class\\s*[:\\-]?\\s*((?:$ROMAN_CLASS|1[0-2]|[1-9]))\\s*-\\s*([A-Za-z])\\b",
            RegexOption.IGNORE_CASE,
        ).find(header)?.let {
            className = it.groupValues[1].trim()
            section = it.groupValues[2].trim().uppercase()
        }
        if (className.isEmpty()) {
            Regex(
                "Class\\s*[:\\-]?\\s*((?:$ROMAN_CLASS|1[0-2]|[1-9]))\\b(?!\\s*Teacher)",
                RegexOption.IGNORE_CASE,
            ).find(header)?.let { className = it.groupValues[1].trim() }
        }

        var examType = ""
        var academicYear = ""
        Regex("Progress\\s*Report\\s*[:\\-]?\\s*(.+)", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)?.let { rest ->
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
            Regex("(\\d{4}\\s*-\\s*\\d{2,4})").find(header)?.let { academicYear = it.groupValues[1].replace(" ", "") }
        }

        val attendanceMatch = Regex("Attendance\\s*[:\\-]?\\s*(\\d+)\\s*/\\s*(\\d+)", RegexOption.IGNORE_CASE).find(text)
        val attendanceDaysPresent = attendanceMatch?.groupValues?.get(1)?.toIntOrNull()
        val attendanceWorkingDays = attendanceMatch?.groupValues?.get(2)?.toIntOrNull()

        var teacherRemarks = captureField(text, "Teacher'?s?\\s*Remarks?", stopWords = emptyList())
        if (teacherRemarks.isEmpty()) {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val idx = lines.indexOfFirst { Regex("Teacher'?s?\\s*Remarks?", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            if (idx in 0 until lines.size - 1) teacherRemarks = lines[idx + 1]
        }

        // Layout-reconstructed rows catch table rows ML Kit split across
        // multiple lines; fall back to (and merge with) the plain-text
        // parse in case reconstruction missed a row the flat text still has.
        val fromLayout = layoutRows.mapNotNull { parseSubjectRow(it) }
        val fromFlatText = parseReportText(text)
        val subjectRows = if (fromLayout.size >= fromFlatText.size) fromLayout else fromFlatText

        val coCurricularRows = parseCoCurricularSection(text, layoutRows)

        return ParsedReportCard(
            studentName = studentName,
            registerNo = registerNo,
            schoolName = schoolName,
            schoolAddress = schoolAddress,
            className = className,
            section = section,
            academicYear = academicYear,
            examType = examType,
            attendanceDaysPresent = attendanceDaysPresent,
            attendanceWorkingDays = attendanceWorkingDays,
            teacherRemarks = teacherRemarks,
            subjectRows = subjectRows,
            coCurricularRows = coCurricularRows,
        )
    }

    // A "legend" token like "A:" (as in "A: Excellent") — marks a grading-key
    // entry rather than an actual activity/trait rating and must be skipped.
    private val legendToken = Regex("^[A-Fa-f]:$")
    private val bareGradeToken = Regex("^[A-Fa-f]$")
    // A blank/ungraded cell prints as a lone dash (e.g. "Value Education -")
    // on real report cards. Without recognizing it as a row terminator (like
    // a real grade letter), the walk keeps absorbing tokens into the label —
    // e.g. "Value Education - Cleanliness" swallowing the *next* activity's
    // name and losing its own row entirely.
    private val ungradedToken = Regex("^-$")

    private val partTwoEndMarker = Regex("Attendance\\b|Teacher'?s?\\s*Remarks?|Signature", RegexOption.IGNORE_CASE)

    /** Best-effort parse of "PART - II Co-Curricular Activities and
     * Character Traits" — a two-column grid of "<activity> <grade>
     * <trait> <grade>" rows plus a grading-key legend ("A: Excellent",
     * "B-Good", ...) mixed into the same lines. Scoped to the row-index
     * range between the "PART - II" and "Attendance"/"Signature" markers
     * within [layoutRows] — without that bound, any unrelated row that
     * happens to end in a single stray A-F letter (e.g. a "... Total ... E"
     * grade summary, or "Class V - E" itself) gets misread as an activity. */
    private fun parseCoCurricularSection(text: String, layoutRows: List<String>): List<ExtractedMarkRow> {
        if (!partTwoStartMarker.containsMatchIn(text)) return emptyList()
        val startIdx = layoutRows.indexOfFirst { partTwoStartMarker.containsMatchIn(it) }
        if (startIdx < 0) return emptyList()
        val afterStart = layoutRows.drop(startIdx + 1)
        val endOffset = afterStart.indexOfFirst { partTwoEndMarker.containsMatchIn(it) }
        val endIdx = if (endOffset < 0) layoutRows.size else startIdx + 1 + endOffset

        val candidateRows = layoutRows.subList((startIdx + 1).coerceAtMost(layoutRows.size), endIdx.coerceIn(0, layoutRows.size))
            .filter { row -> parseSubjectRow(row) == null }

        val results = mutableListOf<ExtractedMarkRow>()
        for (rawRow in candidateRows) {
            val tokens = cleanRow(rawRow).split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
            var labelTokens = mutableListOf<String>()
            var i = 0
            while (i < tokens.size) {
                val tok = tokens[i]
                if (legendToken.matches(tok)) {
                    // Skip the legend marker and its trailing word, e.g. "A: Excellent".
                    i++
                    if (i < tokens.size && tokens[i].all { it.isLetter() }) i++
                    labelTokens = mutableListOf()
                    continue
                }
                if ((bareGradeToken.matches(tok) || ungradedToken.matches(tok)) && labelTokens.isNotEmpty()) {
                    val label = labelTokens.joinToString(" ").trim(',', '-', ':')
                    if (label.length >= 2 && label.any { it.isLetter() } && label.uppercase() !in nonSubjectLineStarts) {
                        results.add(
                            ExtractedMarkRow(
                                subject = label, marksObtained = null, maxMarks = null,
                                grade = if (ungradedToken.matches(tok)) "" else tok.uppercase(),
                                percentage = null, rank = null, remarks = "",
                            ),
                        )
                    }
                    labelTokens = mutableListOf()
                } else {
                    labelTokens.add(tok)
                }
                i++
            }
        }
        // De-duplicate (the candidate-row filter above can surface the same
        // physical row via both the layout and flat-text passes).
        return results.distinctBy { it.subject.uppercase() }
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
