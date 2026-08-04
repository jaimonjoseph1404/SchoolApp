package org.familytools.educationtracker.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportMergeTest {

    private fun row(subject: String, grade: String) =
        ExtractedMarkRow(subject, marksObtained = null, maxMarks = null, grade = grade, percentage = null, rank = null, remarks = "")

    @Test
    fun `falls back to regex result entirely when both AI passes fail`() {
        val regex = ParsedReportCard(studentName = "ARDON JAIMON", subjectRows = listOf(row("English", "B")))
        val merged = mergeReportCards(regex, aiText = null, aiImage = null)
        assertEquals("ARDON JAIMON", merged.studentName)
        assertEquals(1, merged.subjectRows.size)
    }

    @Test
    fun `image result wins over text result over regex for scalar fields`() {
        val regex = ParsedReportCard(studentName = "regex-name", className = "III")
        val text = ParsedReportCard(studentName = "text-name", className = "")
        val image = ParsedReportCard(studentName = "image-name")
        val merged = mergeReportCards(regex, text, image)
        assertEquals("image-name", merged.studentName)
        // image left className blank -> falls through to text (also blank) -> regex
        assertEquals("III", merged.className)
    }

    @Test
    fun `attendance prefers image reading since handwritten numbers are its strength`() {
        val regex = ParsedReportCard(attendanceDaysPresent = 100, attendanceWorkingDays = 110)
        val text = ParsedReportCard(attendanceDaysPresent = 101, attendanceWorkingDays = 111)
        val image = ParsedReportCard(attendanceDaysPresent = 132, attendanceWorkingDays = 142)
        val merged = mergeReportCards(regex, text, image)
        assertEquals(132, merged.attendanceDaysPresent)
        assertEquals(142, merged.attendanceWorkingDays)
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
    fun `image row overwrites a matching-name row from a lower-priority source`() {
        val regex = ParsedReportCard(subjectRows = listOf(row("English", "E")))
        val image = ParsedReportCard(subjectRows = listOf(row("English", "B")))
        val merged = mergeReportCards(regex, aiText = null, aiImage = image)
        assertEquals(1, merged.subjectRows.size)
        assertEquals("B", merged.subjectRows[0].grade)
    }

    @Test
    fun `a subject present in only the regex result is still kept`() {
        val regex = ParsedReportCard(subjectRows = listOf(row("Computer Science", "D")))
        val merged = mergeReportCards(regex, aiText = ParsedReportCard(), aiImage = ParsedReportCard())
        assertEquals(1, merged.subjectRows.size)
        assertEquals("Computer Science", merged.subjectRows[0].subject)
    }

    @Test
    fun `pickInt returns null rather than 0 when nothing has a value`() {
        val merged = mergeReportCards(ParsedReportCard(), null, null)
        assertNull(merged.attendanceDaysPresent)
    }
}
