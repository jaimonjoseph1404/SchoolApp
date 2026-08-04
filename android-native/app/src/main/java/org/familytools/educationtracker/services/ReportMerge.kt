package org.familytools.educationtracker.services

/** Combines the regex parser's result with the (optional) AI results into
 * one [ParsedReportCard]. Per scalar field, prefers whichever non-blank
 * value is available, image-AI first (best shot at handwritten fields like
 * attendance) then text-AI then the regex parser. For subject/co-curricular
 * rows, unions by normalized name across all three sources rather than
 * picking one whole list — a subject only the regex parser found (or only
 * one AI pass found) still makes it into the result, and image-AI's grade
 * wins where the same subject appears in more than one source. */
fun mergeReportCards(
    regex: ParsedReportCard,
    aiText: ParsedReportCard?,
    aiImage: ParsedReportCard?,
): ParsedReportCard {
    val text = aiText ?: ParsedReportCard()
    val image = aiImage ?: ParsedReportCard()

    fun pickString(vararg values: String): String = values.firstOrNull { it.isNotBlank() } ?: ""
    fun pickInt(vararg values: Int?): Int? = values.firstOrNull { it != null }

    return ParsedReportCard(
        studentName = pickString(image.studentName, text.studentName, regex.studentName),
        registerNo = pickString(image.registerNo, text.registerNo, regex.registerNo),
        schoolName = pickString(image.schoolName, text.schoolName, regex.schoolName),
        schoolAddress = pickString(image.schoolAddress, text.schoolAddress, regex.schoolAddress),
        className = pickString(image.className, text.className, regex.className),
        section = pickString(image.section, text.section, regex.section),
        academicYear = pickString(image.academicYear, text.academicYear, regex.academicYear),
        examType = pickString(image.examType, text.examType, regex.examType),
        examDate = pickString(image.examDate, text.examDate, regex.examDate),
        attendanceDaysPresent = pickInt(image.attendanceDaysPresent, text.attendanceDaysPresent, regex.attendanceDaysPresent),
        attendanceWorkingDays = pickInt(image.attendanceWorkingDays, text.attendanceWorkingDays, regex.attendanceWorkingDays),
        teacherRemarks = pickString(image.teacherRemarks, text.teacherRemarks, regex.teacherRemarks),
        subjectRows = mergeRowsByName(regex.subjectRows, text.subjectRows, image.subjectRows),
        coCurricularRows = mergeRowsByName(regex.coCurricularRows, text.coCurricularRows, image.coCurricularRows),
    )
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
