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

data class ExtractedReceipt(
    val schoolName: String = "",
    val receiptNumber: String = "",
    val receiptDate: String = "",
    val totalAmount: Double? = null,
)

/**
 * On-device OCR via Google ML Kit — replaces Tesseract from the Kivy build,
 * which had no working Android recipe. No native binary to bundle, and
 * ML Kit's Latin text model ships as part of the app / is fetched once by
 * Play Services, so this works fully offline after first use.
 */
object OcrService {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractText(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        return result.text
    }

    private val markLineRegex = Regex(
        "^(?<subject>[A-Za-z][A-Za-z .&/-]{2,40}?)\\s+" +
            "(?<obtained>\\d{1,3}(?:\\.\\d+)?)\\s*/?\\s*" +
            "(?<max>\\d{1,3}(?:\\.\\d+)?)" +
            "(?:\\s+(?<grade>[A-F][+-]?))?" +
            "(?:\\s+(?<percentage>\\d{1,3}(?:\\.\\d+)?)\\s*%)?" +
            "(?:\\s+(?:Rank\\s*)?(?<rank>\\d{1,3}))?" +
            "\\s*(?<remarks>.*)$",
    )

    fun parseReportText(text: String): List<ExtractedMarkRow> {
        val rows = mutableListOf<ExtractedMarkRow>()
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val match = markLineRegex.matchEntire(line) ?: continue
            val obtained = match.groups["obtained"]?.value?.toDoubleOrNull() ?: continue
            val max = match.groups["max"]?.value?.toDoubleOrNull() ?: continue
            val percentage = match.groups["percentage"]?.value?.toDoubleOrNull()
                ?: if (max != 0.0) (obtained / max) * 100.0 else null
            rows.add(
                ExtractedMarkRow(
                    subject = match.groups["subject"]?.value?.trim().orEmpty(),
                    marksObtained = obtained,
                    maxMarks = max,
                    grade = match.groups["grade"]?.value?.trim().orEmpty(),
                    percentage = percentage,
                    rank = match.groups["rank"]?.value?.toIntOrNull(),
                    remarks = match.groups["remarks"]?.value?.trim().orEmpty(),
                ),
            )
        }
        return rows
    }

    private val receiptNumberRegex = Regex("(?:receipt|invoice)\\s*(?:no\\.?|number|#)\\s*[:\\-]?\\s*([A-Za-z0-9\\-/]+)", RegexOption.IGNORE_CASE)
    private val receiptDateRegex = Regex("date\\s*[:\\-]?\\s*([0-3]?\\d[/-][01]?\\d[/-]\\d{2,4})", RegexOption.IGNORE_CASE)
    private val totalAmountRegex = Regex("(?:total|grand total|amount payable)\\s*[:\\-]?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)

    fun parseReceiptText(text: String): ExtractedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val schoolName = lines.firstOrNull()?.take(120).orEmpty()
        val receiptNumber = receiptNumberRegex.find(text)?.groupValues?.get(1).orEmpty()
        val receiptDate = receiptDateRegex.find(text)?.groupValues?.get(1).orEmpty()
        val totalAmount = totalAmountRegex.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        return ExtractedReceipt(schoolName, receiptNumber, receiptDate, totalAmount)
    }
}
