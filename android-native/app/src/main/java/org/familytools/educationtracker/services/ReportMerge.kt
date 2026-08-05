package org.familytools.educationtracker.services

/** Combines the regex parser's result with the (optional) AI results into
 * one [ParsedReportCard]. **The regex parser is the trusted source of
 * truth** — it's deterministic, so it either read a cell correctly or left
 * it blank; a small on-device model (Qwen3 0.6B / Gemma 4 E2B) can and does
 * hallucinate plausible-looking-but-wrong values on dense tabular data (seen
 * on a real scan: a subject's score silently replaced by a neighboring
 * subject's score). So AI results only ever *fill in a blank the regex
 * parser left*, in priority order: cloud AI (Gemini — a full-size model
 * reading the actual photo, when the user has opted in with their own API
 * key) first, then image-AI (best shot at handwritten fields like
 * attendance), then text-AI — never overwriting a value regex already
 * found. Subject/co-curricular rows are unioned by normalized name so a
 * subject only an AI pass found still makes it in, but regex's row wins
 * outright on any name collision. [aiCloud] is appended (not inserted)
 * as the last parameter so every existing positional call site keeps its
 * original meaning. */
fun mergeReportCards(
    regex: ParsedReportCard,
    aiText: ParsedReportCard?,
    aiImage: ParsedReportCard?,
    aiCloud: ParsedReportCard? = null,
): ParsedReportCard {
    val text = aiText ?: ParsedReportCard()
    val image = aiImage ?: ParsedReportCard()
    val cloud = aiCloud ?: ParsedReportCard()

    fun pickString(vararg values: String): String = values.firstOrNull { it.isNotBlank() } ?: ""
    fun pickInt(vararg values: Int?): Int? = values.firstOrNull { it != null }
    fun pickDouble(vararg values: Double?): Double? = values.firstOrNull { it != null }
    // A name must actually look like a name — a register number ("366/2023-24")
    // or other mostly-numeric text is never a valid pick here, from any source.
    fun pickName(vararg values: String): String = values.firstOrNull { it.isNotBlank() && looksLikeName(it) } ?: ""

    return ParsedReportCard(
        studentName = pickName(regex.studentName, cloud.studentName, image.studentName, text.studentName),
        registerNo = pickString(regex.registerNo, cloud.registerNo, image.registerNo, text.registerNo),
        schoolName = pickString(regex.schoolName, cloud.schoolName, image.schoolName, text.schoolName),
        schoolAddress = pickString(regex.schoolAddress, cloud.schoolAddress, image.schoolAddress, text.schoolAddress),
        className = pickString(regex.className, cloud.className, image.className, text.className),
        section = pickString(regex.section, cloud.section, image.section, text.section),
        academicYear = pickString(regex.academicYear, cloud.academicYear, image.academicYear, text.academicYear),
        examType = pickString(regex.examType, cloud.examType, image.examType, text.examType),
        examDate = pickString(regex.examDate, cloud.examDate, image.examDate, text.examDate),
        attendanceDaysPresent = pickInt(
            regex.attendanceDaysPresent, cloud.attendanceDaysPresent, image.attendanceDaysPresent, text.attendanceDaysPresent,
        ),
        attendanceWorkingDays = pickInt(
            regex.attendanceWorkingDays, cloud.attendanceWorkingDays, image.attendanceWorkingDays, text.attendanceWorkingDays,
        ),
        teacherRemarks = pickString(regex.teacherRemarks, cloud.teacherRemarks, image.teacherRemarks, text.teacherRemarks),
        totalMarksObtained = pickDouble(
            regex.totalMarksObtained, cloud.totalMarksObtained, image.totalMarksObtained, text.totalMarksObtained,
        ),
        totalMaxMarks = pickDouble(regex.totalMaxMarks, cloud.totalMaxMarks, image.totalMaxMarks, text.totalMaxMarks),
        subjectRows = mergeRowsByName(image.subjectRows, text.subjectRows, cloud.subjectRows, regex.subjectRows),
        coCurricularRows = mergeRowsByName(image.coCurricularRows, text.coCurricularRows, cloud.coCurricularRows, regex.coCurricularRows),
    )
}

/** Combines the (already regex+AI merged) results from scanning MULTIPLE
 * photos of the same report — the live document scanner supports capturing
 * several pages/attempts in one session, e.g. a clear shot of Part-I and a
 * closer one of Part-II, or simply retrying a blurry angle. The first page
 * to successfully read a field wins that field (a later, possibly worse
 * shot can never clobber an earlier good read); subject/co-curricular rows
 * are unioned across every page so a subject only one page's framing caught
 * still makes it into the result. */
fun combineScannedPages(pages: List<ParsedReportCard>): ParsedReportCard {
    if (pages.isEmpty()) return ParsedReportCard()
    if (pages.size == 1) return pages[0]

    fun pickString(selector: (ParsedReportCard) -> String) = pages.map(selector).firstOrNull { it.isNotBlank() } ?: ""
    fun pickInt(selector: (ParsedReportCard) -> Int?) = pages.map(selector).firstOrNull { it != null }
    fun pickDouble(selector: (ParsedReportCard) -> Double?) = pages.map(selector).firstOrNull { it != null }
    fun pickName(selector: (ParsedReportCard) -> String) =
        pages.map(selector).firstOrNull { it.isNotBlank() && looksLikeName(it) } ?: ""

    // mergeRowsByName lets *later* sources win on a name collision; pages
    // are reversed here so the actual first page (highest priority) is
    // applied last and so wins, matching every other field's "first wins".
    val reversedRowSources = pages.asReversed()
    return ParsedReportCard(
        studentName = pickName { it.studentName },
        registerNo = pickString { it.registerNo },
        schoolName = pickString { it.schoolName },
        schoolAddress = pickString { it.schoolAddress },
        className = pickString { it.className },
        section = pickString { it.section },
        academicYear = pickString { it.academicYear },
        examType = pickString { it.examType },
        examDate = pickString { it.examDate },
        attendanceDaysPresent = pickInt { it.attendanceDaysPresent },
        attendanceWorkingDays = pickInt { it.attendanceWorkingDays },
        teacherRemarks = pickString { it.teacherRemarks },
        totalMarksObtained = pickDouble { it.totalMarksObtained },
        totalMaxMarks = pickDouble { it.totalMaxMarks },
        subjectRows = mergeRowsByName(*reversedRowSources.map { it.subjectRows }.toTypedArray()),
        coCurricularRows = mergeRowsByName(*reversedRowSources.map { it.coCurricularRows }.toTypedArray()),
    )
}

/** At least 2 letters, and more letters than digits — rejects a register
 * number or other numeric/ID-like text masquerading as a name. */
private fun looksLikeName(s: String): Boolean {
    val letters = s.count { it.isLetter() }
    val digits = s.count { it.isDigit() }
    return letters >= 2 && letters > digits
}

/** Later lists win on overlapping (normalized) subject names, but rows only
 * present in an earlier list are still kept — a true union, not a swap. */
private fun mergeRowsByName(vararg sources: List<ExtractedMarkRow>): List<ExtractedMarkRow> {
    val byName = LinkedHashMap<String, ExtractedMarkRow>()
    for (rows in sources) {
        for (row in rows) {
            val key = row.subject.trim().uppercase()
            if (key.isEmpty()) continue
            byName[key] = row
        }
    }
    return byName.values.toList()
}
