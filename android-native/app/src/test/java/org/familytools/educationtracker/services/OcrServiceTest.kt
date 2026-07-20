package org.familytools.educationtracker.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for OCR text parsing, built from a hand-transcribed
 * approximation of what ML Kit's line grouping produces for a real ICSE
 * progress report photo (see requirement.md sample) — catches parsing
 * bugs no in-app screenshot would (e.g. captureField's "$" only matching
 * end-of-input, not end-of-line, in a multi-line OCR blob). */
class OcrServiceTest {

    private val sampleReportText = """
        6
        AUXILIUM SCHOOL(ICSE) KA098
        Bandapura Village, Old Madras Road, Virgonagar PO
        Bangalore - 560 049 Ph: 8497825305
        Progress Report : HALF YEARLY EXAMINATION - 2025-2026
        Student Name ARTHUR JAIMON Class V - E
        Register No 234/2021-22
        S.No Part-I Max Min Score Avg Grade
        1 English - I Theory 100 40 55 55 C
        2 English - II Theory 100 40 63 63 B
        3 Kannada Theory 100 40 4 4 E
        4 Hindi Theory 100 40 10 10 E
        5 Mathematics Theory 100 40 43 43 D
        6 Science Theory 100 40 33 33 E
        7 Social Studies Theory 100 40 50 50 C
        8 Computer Science Theory 100 40 42 42 D
        9 General Knowledge Theory 100 40 48 48 D
        Total 900 360 348 E
        PART - II Co - Curricular Activities and Character Traits
        PT / Games A Courteous B A: Excellent
        Reading & Recitation A Obedience A B-Good
        II Lang Sp. Writing B Leadership C C: Fair
        III Lang Sp. Writing B Punctuality D D: Satisfactory
        Value Education B Cleanliness B E: Unsatisfactory
        Attendance 121/126
        Teacher's Remarks With hard work and interest you are sure to do well next term.
        Signature : Class Teacher Principal Parent
    """.trimIndent()

    @Test
    fun `extracts student name stopping before Class label on same line`() {
        val parsed = OcrService.parseProgressReport(sampleReportText)
        assertEquals("ARTHUR JAIMON", parsed.studentName)
    }

    @Test
    fun `extracts register number`() {
        val parsed = OcrService.parseProgressReport(sampleReportText)
        assertEquals("234/2021-22", parsed.registerNo)
    }

    @Test
    fun `extracts class and section`() {
        val parsed = OcrService.parseProgressReport(sampleReportText)
        assertEquals("V", parsed.className)
        assertEquals("E", parsed.section)
    }

    @Test
    fun `extracts exam type and academic year separately`() {
        val parsed = OcrService.parseProgressReport(sampleReportText)
        assertEquals("HALF YEARLY EXAMINATION", parsed.examType)
        assertEquals("2025-2026", parsed.academicYear)
    }

    @Test
    fun `extracts attendance as separate days-present and working-days fields`() {
        val parsed = OcrService.parseProgressReport(sampleReportText)
        assertEquals(121, parsed.attendanceDaysPresent)
        assertEquals(126, parsed.attendanceWorkingDays)
    }

    @Test
    fun `extracts school name and address from the header block`() {
        val parsed = OcrService.parseProgressReport(sampleReportText)
        assertEquals("AUXILIUM SCHOOL(ICSE) KA098", parsed.schoolName)
        assertTrue(parsed.schoolAddress.contains("Bandapura Village"))
    }

    @Test
    fun `extracts all ten Part-II co-curricular activity and character-trait ratings`() {
        val parsed = OcrService.parseProgressReport(sampleReportText)
        assertEquals(10, parsed.coCurricularRows.size)

        val games = parsed.coCurricularRows.first { it.subject == "PT / Games" }
        assertEquals("A", games.grade)
        val leadership = parsed.coCurricularRows.first { it.subject == "Leadership" }
        assertEquals("C", leadership.grade)

        // Legend entries ("A: Excellent", "C: Fair", ...) must not appear as rows.
        assertTrue(parsed.coCurricularRows.none { it.subject.equals("Excellent", ignoreCase = true) })
        assertTrue(parsed.coCurricularRows.none { it.subject.equals("Fair", ignoreCase = true) })
    }

    @Test
    fun `does not let an unrelated number elsewhere on the page pollute the class field`() {
        // Regression: a real scan matched "36" (from unrelated page content)
        // as the class instead of "III - C" once "Class" appeared anywhere
        // in the whole OCR blob. Header-scoping + a strict roman-numeral or
        // 1-12 digit pattern (not "any 1-4 digit/roman run") fixes it.
        val text = """
            Progress Report : ANNUAL EXAMINATION - 2025-2026
            Student Name JANE DOE Class III - C
            Register No 55/2024-25
            S.No Part-I Max Min Score Avg Grade
            1 English 100 40 70 70 B
            Total Working Days 36
            Attendance 30/36
        """.trimIndent()
        val parsed = OcrService.parseProgressReport(text)
        assertEquals("III", parsed.className)
        assertEquals("C", parsed.section)
    }

    @Test
    fun `extracts full teacher remarks sentence, not just the first word`() {
        val parsed = OcrService.parseProgressReport(sampleReportText)
        assertEquals("With hard work and interest you are sure to do well next term.", parsed.teacherRemarks)
    }

    @Test
    fun `extracts all nine subject rows with correct values, skipping header and total`() {
        val parsed = OcrService.parseProgressReport(sampleReportText)
        assertEquals(9, parsed.subjectRows.size)

        val english1 = parsed.subjectRows.first { it.subject == "English - I" }
        assertEquals(55.0, english1.marksObtained)
        assertEquals(100.0, english1.maxMarks)
        assertEquals("C", english1.grade)

        val kannada = parsed.subjectRows.first { it.subject == "Kannada" }
        assertEquals(4.0, kannada.marksObtained)
        assertEquals("E", kannada.grade)

        val gk = parsed.subjectRows.first { it.subject == "General Knowledge" }
        assertEquals(48.0, gk.marksObtained)
        assertEquals("D", gk.grade)

        assertTrue(parsed.subjectRows.none { it.subject.uppercase().startsWith("TOTAL") })
    }

    @Test
    fun `strips table-border pipe characters before parsing a subject row`() {
        val row = "| 1 | English - I | Theory | 100 | 40 | 55 | 55 | C |"
        val parsed = OcrService.parseReportText(row)
        assertEquals(1, parsed.size)
        assertEquals("English - I", parsed[0].subject)
        assertEquals(55.0, parsed[0].marksObtained)
        assertEquals("C", parsed[0].grade)
    }

    @Test
    fun `accepts CBSE-style two-character grades like A1 and B2`() {
        val row = "1 Mathematics 100 40 92 92 A1"
        val parsed = OcrService.parseReportText(row)
        assertEquals(1, parsed.size)
        assertEquals("A1", parsed[0].grade)
    }

    @Test
    fun `does not reject a subject name containing a stray OCR'd digit`() {
        val row = "3 S0cial Studies 100 40 50 50 C"
        val parsed = OcrService.parseReportText(row)
        assertEquals(1, parsed.size)
        assertEquals("S0cial Studies", parsed[0].subject)
    }

    @Test
    fun `recognizes alternate student-name labels`() {
        val text = "Name of the Student : ARTHUR JAIMON\nClass V - E"
        val parsed = OcrService.parseProgressReport(text)
        assertEquals("ARTHUR JAIMON", parsed.studentName)
    }

    @Test
    fun `prefers layout-reconstructed rows when they find more subjects than the flat text`() {
        // Simulates ML Kit splitting one visual table row across two text
        // lines (a real, common failure mode for photographed tables) —
        // the flat text alone can't recover the row, but a bounding-box
        // reconstruction (passed here as layoutRows) can.
        val brokenFlatText = "1 English - I Theory\n100 40 55 55 C"
        val reconstructedRows = listOf("1 English - I Theory 100 40 55 55 C")

        val fromFlatTextOnly = OcrService.parseProgressReport(brokenFlatText)
        assertEquals(0, fromFlatTextOnly.subjectRows.size)

        val fromLayout = OcrService.parseProgressReport(brokenFlatText, reconstructedRows)
        assertEquals(1, fromLayout.subjectRows.size)
        assertEquals("English - I", fromLayout.subjectRows[0].subject)
    }

    @Test
    fun `parseReceiptText extracts amount, date and suggests a category`() {
        val text = """
            Auxilium School Fee Receipt
            Receipt No: FR-2026-118
            Date: 12/07/2026
            Student Name ARTHUR JAIMON
            Particulars: Tuition Fee Term 2
            Total: Rs. 15,000.00
        """.trimIndent()
        val receipt = OcrService.parseReceiptText(text)
        assertEquals("ARTHUR JAIMON", receipt.studentName)
        assertEquals("FR-2026-118", receipt.receiptNumber)
        assertEquals("12/07/2026", receipt.receiptDate)
        assertEquals(15000.0, receipt.totalAmount)
        assertEquals("Tuition Fee", receipt.suggestedCategory)
    }
}
