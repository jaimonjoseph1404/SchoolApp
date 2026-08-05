package org.familytools.educationtracker.services

import org.familytools.educationtracker.data.AcademicDao
import org.familytools.educationtracker.data.ExpenseDao
import org.familytools.educationtracker.data.MarkHistoryRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/** None of the functions under test here touch the DAOs (they operate
 * purely on the [MarkHistoryRow] list already passed in) — a dynamic proxy
 * that throws if ever actually called is enough, no mocking library needed
 * just to satisfy AnalyticsEngine's constructor. */
private inline fun <reified T> unusedDao(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ ->
        throw UnsupportedOperationException("not used by these tests")
    } as T

class AnalyticsEngineTest {

    private fun row(
        subject: String,
        marks: Double,
        max: Double = 100.0,
        term: String = "Term 1",
        examType: String = "Unit Test I",
        year: String = "2025-26",
    ) = MarkHistoryRow(
        yearLabel = year, className = "III", termName = term, examType = examType, examDate = "",
        subjectName = subject, marksObtained = marks, maxMarks = max, grade = "", percentage = marks / max * 100,
        rank = null, remarks = "",
    )

    private val engine = AnalyticsEngine(unusedDao<AcademicDao>(), unusedDao<ExpenseDao>())

    @Test
    fun `improvementActions returns nothing for a strong, stable subject`() {
        val rows = listOf(row("English", 85.0, term = "Term 1"), row("English", 88.0, term = "Term 2"))
        val actions = engine.improvementActions(rows)
        assertTrue(actions.none { it.contains("English") })
    }

    @Test
    fun `improvementActions flags a weak subject even without a declining trend`() {
        val rows = listOf(row("Kannada", 30.0))
        val actions = engine.improvementActions(rows)
        assertTrue(actions.any { it.contains("Kannada") && it.contains("averaging") })
    }

    @Test
    fun `improvementActions flags a declining subject that is not yet weak`() {
        // Two terms, clearly dropping, both still above the 50% "weak" floor.
        val rows = listOf(row("Science", 90.0, term = "Term 1"), row("Science", 60.0, term = "Term 2"))
        val actions = engine.improvementActions(rows)
        assertTrue(actions.any { it.contains("Science") && it.contains("trending down") })
    }

    @Test
    fun `improvementActions combines low-and-declining into one urgent message`() {
        val rows = listOf(row("Hindi", 55.0, term = "Term 1"), row("Hindi", 20.0, term = "Term 2"))
        val actions = engine.improvementActions(rows)
        assertTrue(actions.any { it.contains("Hindi") && it.contains("low") && it.contains("declining") })
    }

    @Test
    fun `improvementActions reassures when every subject is healthy`() {
        val rows = listOf(row("English", 85.0), row("Maths", 90.0))
        val actions = engine.improvementActions(rows)
        assertEquals(1, actions.size)
        assertTrue(actions[0].contains("No subject"))
    }

    @Test
    fun `improvementActions returns nothing at all when there are no marks yet`() {
        assertEquals(emptyList<String>(), engine.improvementActions(emptyList()))
    }
}
