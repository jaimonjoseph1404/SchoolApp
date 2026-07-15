package org.familytools.educationtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expense_categories")
data class ExpenseCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val academicYearId: Long? = null,
    val classId: Long? = null,
    val categoryId: Long,
    val amount: Double,
    val expenseDate: String = "",
    val description: String = "",
    val receiptPath: String = "",
)

@Entity(tableName = "fee_receipts")
data class FeeReceipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val schoolName: String = "",
    val receiptNumber: String = "",
    val receiptDate: String = "",
    val amount: Double? = null,
    val totalAmount: Double? = null,
    val imagePath: String = "",
)

data class ExpenseRow(
    val id: Long,
    val categoryName: String,
    val amount: Double,
    val expenseDate: String,
    val description: String,
    val yearLabel: String?,
)

data class CategoryTotal(val categoryName: String, val total: Double)
data class YearTotal(val yearLabel: String, val total: Double)

val DEFAULT_EXPENSE_CATEGORIES = listOf(
    "Tuition Fee", "Admission Fee", "Books", "Uniform", "Bus Fee",
    "Examination Fee", "Activity Fee", "Sports Fee", "Laboratory Fee",
    "Technology Fee", "Coaching Fee", "Miscellaneous",
)
