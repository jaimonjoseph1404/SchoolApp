package org.familytools.educationtracker.services

import android.content.Context
import org.familytools.educationtracker.data.AppDatabase

data class SearchResult(val category: String, val label: String, val detail: String, val refId: Long? = null)

object SearchService {
    fun search(context: Context, query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val db = AppDatabase.getInstance(context).openHelper.readableDatabase
        val like = "%$query%"
        val results = mutableListOf<SearchResult>()

        db.query("SELECT id, fullName, currentClass, schoolName FROM children WHERE fullName LIKE ?", arrayOf(like)).use {
            while (it.moveToNext()) {
                results.add(
                    SearchResult(
                        "Child", it.getString(1),
                        listOfNotNull(it.getString(2).ifBlank { null }, it.getString(3).ifBlank { null }).joinToString(" · "),
                        it.getLong(0),
                    ),
                )
            }
        }
        db.query("SELECT id, name FROM subjects WHERE name LIKE ?", arrayOf(like)).use {
            while (it.moveToNext()) results.add(SearchResult("Subject", it.getString(1), "", it.getLong(0)))
        }
        db.query("SELECT id, name, subject, schoolName FROM teachers WHERE name LIKE ?", arrayOf(like)).use {
            while (it.moveToNext()) {
                results.add(
                    SearchResult(
                        "Teacher", it.getString(1),
                        listOfNotNull(it.getString(2).ifBlank { null }, it.getString(3).ifBlank { null }).joinToString(" · "),
                        it.getLong(0),
                    ),
                )
            }
        }
        db.query("SELECT DISTINCT yearLabel FROM academic_years WHERE yearLabel LIKE ?", arrayOf(like)).use {
            while (it.moveToNext()) results.add(SearchResult("Academic Year", it.getString(0), ""))
        }
        db.query("SELECT DISTINCT schoolName FROM children WHERE schoolName LIKE ? AND schoolName != ''", arrayOf(like)).use {
            while (it.moveToNext()) results.add(SearchResult("School", it.getString(0), ""))
        }
        db.query("SELECT id, name FROM expense_categories WHERE name LIKE ?", arrayOf(like)).use {
            while (it.moveToNext()) results.add(SearchResult("Expense Category", it.getString(1), "", it.getLong(0)))
        }
        return results
    }
}
