package org.familytools.educationtracker.services

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Structures a scanned report card into [ParsedReportCard] using Google's
 * Gemini API — reads the photo directly with a full-size cloud vision model,
 * far more reliably than the small on-device models this app also supports
 * (Qwen3 0.6B / Gemma 4 E2B). Strictly opt-in: only called when the user has
 * saved their own API key in Settings, since the report photo leaves the
 * device for this request. Uses a plain HTTPS POST (via [HttpURLConnection]
 * and org.json) rather than Google's Android SDK — the official
 * `generativeai` package is deprecated in favor of a full Firebase project
 * setup, which is disproportionate for one REST call. Every entry point
 * returns null on any failure (no key, network error, malformed response) so
 * the caller always falls back to the regex parser / on-device AI. */
object GeminiReportParser {
    private const val TIMEOUT_MS = 45_000L
    private const val MODEL = "gemini-3.6-flash"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    // No responseSchema here (Gemini's structured-output schema dialect has
    // shifted across versions) — responseMimeType alone reliably forces JSON,
    // and the exact shape is spelled out in the prompt instead, then parsed
    // defensively below exactly like AiReportParser's on-device JSON.
    private val prompt = """
        You are reading a photo of a school progress report card. Read every
        field directly from the image and return ONLY a single JSON object
        (no markdown fences, no commentary) with exactly this shape:
        {
          "studentName": string, "registerNo": string, "schoolName": string,
          "schoolAddress": string, "className": string, "section": string,
          "academicYear": string, "examType": string,
          "attendanceDaysPresent": number or null, "attendanceWorkingDays": number or null,
          "teacherRemarks": string, "totalMarksObtained": number or null, "totalMaxMarks": number or null,
          "subjects": [{"name": string, "max": number or null, "min": number or null, "score": number or null, "grade": string}],
          "coCurricular": [{"activity": string, "grade": string}]
        }
        Rules:
        - A subject cell reading "AB" means the student was Absent for that
          exam: set score to null and grade to "AB" — never drop the subject.
        - A blank cell or a lone dash "-" means ungraded: use an empty string
          for that field, do not guess a value.
        - "className" is a Roman numeral (e.g. "III", "V") or a plain class
          number (1-12); never combine it with the section letter.
        - Only list subjects/activities actually printed on the report.
        - "totalMarksObtained"/"totalMaxMarks" come from the report's own
          "Total" row — read it directly, do not sum the subjects yourself.
    """.trimIndent()

    suspend fun structureFromImage(context: Context, uri: Uri, apiKey: String): ParsedReportCard? {
        if (apiKey.isBlank()) return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@withTimeoutOrNull null
                    val requestBody = buildRequestBody(bytes)
                    val responseText = post(requestBody, apiKey) ?: return@withTimeoutOrNull null
                    parseResponse(responseText)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun buildRequestBody(imageBytes: ByteArray): String {
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", base64Image)))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        return body.toString()
    }

    private fun post(requestBody: String, apiKey: String): String? {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 30_000
        }
        return try {
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val ok = connection.responseCode in 200..299
            val stream = if (ok) connection.inputStream else connection.errorStream
            val responseText = stream?.use { it.readBytes().toString(Charsets.UTF_8) }
            if (ok) responseText else null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(responseText: String): ParsedReportCard? {
        return try {
            val root = JSONObject(responseText)
            val text = root.optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")
                ?.optJSONArray("parts")?.optJSONObject(0)
                ?.optString("text")?.trim().orEmpty()
            if (text.isEmpty()) return null
            val json = JSONObject(text)
            ParsedReportCard(
                studentName = json.optString("studentName"),
                registerNo = json.optString("registerNo"),
                schoolName = json.optString("schoolName"),
                schoolAddress = json.optString("schoolAddress"),
                className = json.optString("className"),
                section = json.optString("section"),
                academicYear = json.optString("academicYear"),
                examType = json.optString("examType"),
                attendanceDaysPresent = json.optIntOrNull("attendanceDaysPresent"),
                attendanceWorkingDays = json.optIntOrNull("attendanceWorkingDays"),
                teacherRemarks = json.optString("teacherRemarks"),
                totalMarksObtained = json.optDoubleOrNull("totalMarksObtained"),
                totalMaxMarks = json.optDoubleOrNull("totalMaxMarks"),
                subjectRows = json.optJSONArray("subjects")?.toMarkRows("name") ?: emptyList(),
                coCurricularRows = json.optJSONArray("coCurricular")?.toMarkRows("activity") ?: emptyList(),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONArray.toMarkRows(nameKey: String): List<ExtractedMarkRow> {
        val rows = mutableListOf<ExtractedMarkRow>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val name = item.optString(nameKey).trim()
            if (name.isEmpty()) continue
            rows.add(
                ExtractedMarkRow(
                    subject = name,
                    marksObtained = item.optDoubleOrNull("score"),
                    maxMarks = item.optDoubleOrNull("max"),
                    grade = item.optString("grade"),
                    percentage = null,
                    rank = null,
                    remarks = "",
                ),
            )
        }
        return rows
    }
}
