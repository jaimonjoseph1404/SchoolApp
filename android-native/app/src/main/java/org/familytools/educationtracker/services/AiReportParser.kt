package org.familytools.educationtracker.services

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ResponseFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/** Structures a scanned report card into [ParsedReportCard] using an
 * on-device LLM (LiteRT-LM) instead of regex — either from the ML Kit OCR
 * text, or by reading the photo directly with a vision-capable model. Purely
 * additive: every entry point returns null on any failure (model not
 * downloaded, engine init failure, timeout, malformed response) so the
 * caller always has [OcrService]'s regex parser to fall back to. */
object AiReportParser {
    private const val TIMEOUT_MS = 45_000L

    private var textEngine: Engine? = null
    private var visionEngine: Engine? = null
    private var textEngineFailed = false
    private var visionEngineFailed = false

    // Kept intentionally small/flat — this is what the model must fill in,
    // matching ParsedReportCard/ExtractedMarkRow so no separate model exists.
    private val reportSchema = """
        {
          "type": "object",
          "properties": {
            "studentName": {"type": "string"},
            "registerNo": {"type": "string"},
            "schoolName": {"type": "string"},
            "schoolAddress": {"type": "string"},
            "className": {"type": "string"},
            "section": {"type": "string"},
            "academicYear": {"type": "string"},
            "examType": {"type": "string"},
            "attendanceDaysPresent": {"type": ["integer", "null"]},
            "attendanceWorkingDays": {"type": ["integer", "null"]},
            "teacherRemarks": {"type": "string"},
            "subjects": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "name": {"type": "string"},
                  "max": {"type": ["number", "null"]},
                  "min": {"type": ["number", "null"]},
                  "score": {"type": ["number", "null"]},
                  "grade": {"type": "string"}
                },
                "required": ["name"]
              }
            },
            "coCurricular": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "activity": {"type": "string"},
                  "grade": {"type": "string"}
                },
                "required": ["activity"]
              }
            }
          }
        }
    """.trimIndent()

    private fun promptFor(ocrText: String): String = """
        You are reading a school progress report card. Extract the data as
        strict JSON matching the given schema. Rules:
        - A subject cell reading "AB" means the student was Absent for that
          exam: set score to null and grade to "AB" — never drop the subject.
        - A blank cell or a lone dash "-" means ungraded: use an empty string
          for that field, do not guess a value.
        - "className" is a Roman numeral (e.g. "III", "V") or a plain class
          number (1-12); never combine it with the section letter.
        - Only list subjects/activities actually printed on the report.

        $ocrText
    """.trimIndent()

    private fun getTextEngine(context: Context): Engine? {
        if (textEngineFailed) return null
        textEngine?.let { return it }
        return try {
            val path = AiModelManager.localFile(context, AiModel.TEXT).absolutePath
            val engine = Engine(EngineConfig(path, Backend.CPU(), null, null, null, null, null))
            engine.initialize()
            textEngine = engine
            engine
        } catch (e: Exception) {
            textEngineFailed = true
            null
        }
    }

    private fun getVisionEngine(context: Context): Engine? {
        if (visionEngineFailed) return null
        visionEngine?.let { return it }
        return try {
            val path = AiModelManager.localFile(context, AiModel.VISION).absolutePath
            val engine = Engine(EngineConfig(path, Backend.CPU(), Backend.CPU(), null, null, null, null))
            engine.initialize()
            visionEngine = engine
            engine
        } catch (e: Exception) {
            visionEngineFailed = true
            null
        }
    }

    suspend fun structureFromText(context: Context, ocrText: String): ParsedReportCard? {
        if (ocrText.isBlank() || !AiModelManager.isReady(context, AiModel.TEXT)) return null
        return withContext(Dispatchers.Default) {
            withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    val engine = getTextEngine(context) ?: return@withTimeoutOrNull null
                    engine.createConversation(ConversationConfig()).use { conversation ->
                        val response = conversation.sendMessage(
                            promptFor(ocrText), emptyMap<String, Any>(), null, null, null, null, null,
                            ResponseFormat.json(reportSchema),
                        )
                        parseResponse(response)
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    suspend fun structureFromImage(context: Context, uri: Uri): ParsedReportCard? {
        if (!AiModelManager.isReady(context, AiModel.VISION)) return null
        return withContext(Dispatchers.Default) {
            withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@withTimeoutOrNull null
                    val engine = getVisionEngine(context) ?: return@withTimeoutOrNull null
                    engine.createConversation(ConversationConfig()).use { conversation ->
                        val contents = Contents.of(
                            Content.Text(promptFor("(read the report directly from the attached photo)")),
                            Content.ImageBytes(bytes),
                        )
                        val response = conversation.sendMessage(
                            contents, emptyMap<String, Any>(), null, null, null, null, null,
                            ResponseFormat.json(reportSchema),
                        )
                        parseResponse(response)
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun parseResponse(message: Message): ParsedReportCard? {
        val raw = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.trim()
        if (raw.isEmpty()) return null
        return try {
            val json = JSONObject(raw)
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
