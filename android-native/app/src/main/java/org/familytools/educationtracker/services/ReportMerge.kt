package org.familytools.educationtracker.services

/** Combines the regex parser's result with the (optional) AI results into
 * one [ParsedReportCard]. **The regex parser is the trusted source of
 * truth** — it's deterministic, so it either read a cell correctly or left
 * it blank; a small on-device model (Qwen3 0.6B / Gemma 4 E2B) can and does
 * hallucinate plausible-looking-but-wrong values on dense tabular data (seen
 * on a real scan: a subject's score silently replaced by a neighboring
 * subject's score). So AI results only ever *fill in a blank the regex
 * parser left* — image-AI first (best shot at handwritten fields like
 * attendance), then text-AI — never overwrite a value regex already found.
 * Subject/co-curricular rows are unioned by normalized name so a subject
 * only an AI pass found still makes it in, but regex's row wins outright on
 * any name collision. */
fun mergeReportCards(
    regex: ParsedReportCard,
    aiText: ParsedReportCard?,
    aiImage: ParsedReportCard?,
): ParsedReportCard {
    val text = aiText ?: ParsedReportCard()
    val image = aiImage ?: ParsedReportCard()

    fun pickString(vararg values: String): String = values.firstOrNull { it.isNotBlank() } ?: ""
    fun pickInt(vararg values: Int?): Int? = values.firstOrNull { it != null }
    // A name must actually look like a name — a register number ("366/2023-24")
    // or other mostly-numeric text is never a valid pick here, from any source.
    fun pickName(vararg values: String): String = values.firstOrNull { it.isNotBlank() && looksLikeName(it) } ?: ""

    return ParsedReportCard(
        studentName = pickName(regex.studentName, image.studentName, text.studentName),
        registerNo = pickString(regex.registerNo, image.registerNo, text.registerNo),
        schoolName = pickString(regex.schoolName, image.schoolName, text.schoolName),
        schoolAddress = pickString(regex.schoolAddress, image.schoolAddress, text.schoolAddress),
        className = pickString(regex.className, image.className, text.className),
        section = pickString(regex.section, image.section, text.section),
        academicYear = pickString(regex.academicYear, image.academicYear, text.academicYear),
        examType = pickString(regex.examType, image.examType, text.examType),
        examDate = pickString(regex.examDate, image.examDate, text.examDate),
        attendanceDaysPresent = pickInt(regex.attendanceDaysPresent, image.attendanceDaysPresent, text.attendanceDaysPresent),
        attendanceWorkingDays = pickInt(regex.attendanceWorkingDays, image.attendanceWorkingDays, text.attendanceWorkingDays),
        teacherRemarks = pickString(regex.teacherRemarks, image.teacherRemarks, text.teacherRemarks),
        subjectRows = mergeRowsByName(image.subjectRows, text.subjectRows, regex.subjectRows),
        coCurricularRows = mergeRowsByName(image.coCurricularRows, text.coCurricularRows, regex.coCurricularRows),
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
