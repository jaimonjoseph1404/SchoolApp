package org.familytools.educationtracker.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportMergeTest {

    private fun row(subject: String, grade: String) =
        ExtractedMarkRow(subject, marksObtained = null, maxMarks = null, grade = grade, percentage = null, rank = null, remarks = "")

    @Test
    fun `combineScannedPages returns the single page unchanged`() {
        val page = ParsedReportCard(studentName = "ARDON JAIMON")
        assertEquals(page, combineScannedPages(listOf(page)))
    }

    @Test
    fun `combineScannedPages keeps the first page's field, not a later page's`() {
        // A closer second shot of Part-II shouldn't be able to clobber a
        // good first-page reading of the student name/class.
        val first = ParsedReportCard(studentName = "ARDON JAIMON", className = "III")
        val second = ParsedReportCard(studentName = "SOMEONE ELSE", className = "V")
        val combined = combineScannedPages(listOf(first, second))
        assertEquals("ARDON JAIMON", combined.studentName)
        assertEquals("III", combined.className)
    }

    @Test
    fun `combineScannedPages fills a field only the second page found`() {
        val first = ParsedReportCard(attendanceDaysPresent = null)
        val second = ParsedReportCard(attendanceDaysPresent = 26, attendanceWorkingDays = 26)
        val combined = combineScannedPages(listOf(first, second))
        assertEquals(26, combined.attendanceDaysPresent)
        assertEquals(26, combined.attendanceWorkingDays)
    }

    @Test
    fun `combineScannedPages unions subject rows across pages, first page wins on collision`() {
        val first = ParsedReportCard(subjectRows = listOf(row("English", "B"), row("Kannada", "E")))
        val second = ParsedReportCard(subjectRows = listOf(row("English", "C"), row("Hindi", "D")))
        val combined = combineScannedPages(listOf(first, second))
        val bySubject = combined.subjectRows.associateBy { it.subject }
        assertEquals(setOf("English", "Kannada", "Hindi"), bySubject.keys)
        assertEquals("B", bySubject.getValue("English").grade) // first page's row wins
    }

    @Test
    fun `falls back to regex result entirely when both AI passes fail`() {
        val regex = ParsedReportCard(studentName = "ARDON JAIMON", subjectRows = listOf(row("English", "B")))
        val merged = mergeReportCards(regex, aiText = null, aiImage = null)
        assertEquals("ARDON JAIMON", merged.studentName)
        assertEquals(1, merged.subjectRows.size)
    }

    @Test
    fun `regex wins over both AI passes for scalar fields when regex has a value`() {
        // The deterministic regex parser is trusted over a small on-device
        // model, which can hallucinate plausible-looking wrong values on
        // dense tabular data — regression from a real scan where AI-supplied
        // text silently overwrote a correct regex-extracted value.
        val regex = ParsedReportCard(studentName = "ARDON JAIMON", className = "III")
        val text = ParsedReportCard(studentName = "text-name", className = "V")
        val image = ParsedReportCard(studentName = "image-name", className = "VI")
        val merged = mergeReportCards(regex, text, image)
        assertEquals("ARDON JAIMON", merged.studentName)
        assertEquals("III", merged.className)
    }

    @Test
    fun `AI fills a field regex left blank, image before text`() {
        val regex = ParsedReportCard(studentName = "ARDON JAIMON", className = "")
        val text = ParsedReportCard(className = "V")
        val image = ParsedReportCard(className = "III")
        val merged = mergeReportCards(regex, text, image)
        assertEquals("III", merged.className)
    }

    @Test
    fun `attendance falls back to AI (image before text) only when regex found nothing`() {
        val regex = ParsedReportCard(attendanceDaysPresent = null, attendanceWorkingDays = null)
        val text = ParsedReportCard(attendanceDaysPresent = 101, attendanceWorkingDays = 111)
        val image = ParsedReportCard(attendanceDaysPresent = 132, attendanceWorkingDays = 142)
        val merged = mergeReportCards(regex, text, image)
        assertEquals(132, merged.attendanceDaysPresent)
        assertEquals(142, merged.attendanceWorkingDays)
    }

    @Test
    fun `attendance keeps the regex reading even if AI disagrees`() {
        val regex = ParsedReportCard(attendanceDaysPresent = 26, attendanceWorkingDays = 26)
        val image = ParsedReportCard(attendanceDaysPresent = 132, attendanceWorkingDays = 142)
        val merged = mergeReportCards(regex, aiText = null, aiImage = image)
        assertEquals(26, merged.attendanceDaysPresent)
        assertEquals(26, merged.attendanceWorkingDays)
    }

    @Test
    fun `rejects a register number or other numeric text masquerading as the student name`() {
        // Regression: a real scan produced studentName = "366/2023-24" (the
        // register number) and it was accepted as-is, creating a bogus child.
        val regex = ParsedReportCard(studentName = "")
        val text = ParsedReportCard(studentName = "366/2023-24")
        val merged = mergeReportCards(regex, text, aiImage = null)
        assertEquals("", merged.studentName)
    }

    @Test
    fun `still accepts a real name from AI when regex found nothing`() {
        val regex = ParsedReportCard(studentName = "")
        val image = ParsedReportCard(studentName = "ARDON JAIMON")
        val merged = mergeReportCards(regex, aiText = null, aiImage = image)
        assertEquals("ARDON JAIMON", merged.studentName)
    }

    @Test
    fun `unions subject rows across all three sources instead of picking one whole list`() {
        val regex = ParsedReportCard(subjectRows = listOf(row("Kannada", "E"), row("Hindi", "B")))
        val text = ParsedReportCard(subjectRows = listOf(row("Mathematics", "D")))
        val image = ParsedReportCard(subjectRows = listOf(row("Science", "E")))
        val merged = mergeReportCards(regex, text, image)
        val names = merged.subjectRows.map { it.subject }.toSet()
        assertEquals(setOf("Kannada", "Hindi", "Mathematics", "Science"), names)
    }

    @Test
    fun `regex row wins over a matching-name AI row instead of being overwritten`() {
        // Regression: a real scan's English-II row ended up with Kannada's
        // score after merging — the AI's row for the same subject name was
        // silently winning over the (correct) regex-extracted row.
        val regex = ParsedReportCard(subjectRows = listOf(row("English - II", "E")))
        val image = ParsedReportCard(subjectRows = listOf(row("English - II", "B")))
        val merged = mergeReportCards(regex, aiText = null, aiImage = image)
        assertEquals(1, merged.subjectRows.size)
        assertEquals("E", merged.subjectRows[0].grade)
    }

    @Test
    fun `AI-only row (a subject regex completely missed) still makes it into the result`() {
        val regex = ParsedReportCard(subjectRows = listOf(row("English", "B")))
        val image = ParsedReportCard(subjectRows = listOf(row("Kannada", "E")))
        val merged = mergeReportCards(regex, aiText = null, aiImage = image)
        val names = merged.subjectRows.map { it.subject }.toSet()
        assertEquals(setOf("English", "Kannada"), names)
    }

    @Test
    fun `a subject present in only the regex result is still kept`() {
        val regex = ParsedReportCard(subjectRows = listOf(row("Computer Science", "D")))
        val merged = mergeReportCards(regex, aiText = ParsedReportCard(), aiImage = ParsedReportCard())
        assertEquals(1, merged.subjectRows.size)
        assertEquals("Computer Science", merged.subjectRows[0].subject)
    }

    @Test
    fun `totalMarksObtained and totalMaxMarks follow the same regex-first fallback rule as attendance`() {
        val regex = ParsedReportCard(totalMarksObtained = 348.0, totalMaxMarks = 900.0)
        val image = ParsedReportCard(totalMarksObtained = 999.0, totalMaxMarks = 999.0)
        val merged = mergeReportCards(regex, aiText = null, aiImage = image)
        assertEquals(348.0, merged.totalMarksObtained)
        assertEquals(900.0, merged.totalMaxMarks)
    }

    @Test
    fun `cloud AI (Gemini) fills a blank regex left, ahead of the on-device image and text passes`() {
        val regex = ParsedReportCard(className = "")
        val cloud = ParsedReportCard(className = "III")
        val image = ParsedReportCard(className = "V")
        val text = ParsedReportCard(className = "VI")
        val merged = mergeReportCards(regex, aiText = text, aiImage = image, aiCloud = cloud)
        assertEquals("III", merged.className)
    }

    @Test
    fun `regex still wins over cloud AI when regex already found a value`() {
        val regex = ParsedReportCard(studentName = "ARDON JAIMON")
        val cloud = ParsedReportCard(studentName = "WRONG NAME")
        val merged = mergeReportCards(regex, aiText = null, aiImage = null, aiCloud = cloud)
        assertEquals("ARDON JAIMON", merged.studentName)
    }

    @Test
    fun `pickInt returns null rather than 0 when nothing has a value`() {
        val merged = mergeReportCards(ParsedReportCard(), null, null)
        assertNull(merged.attendanceDaysPresent)
    }
}
