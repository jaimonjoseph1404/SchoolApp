package org.familytools.educationtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense_categories ORDER BY name")
    fun observeCategories(): Flow<List<ExpenseCategory>>

    @Query("SELECT id FROM expense_categories WHERE name = :name LIMIT 1")
    suspend fun findCategory(name: String): Long?

    @Insert
    suspend fun insertCategory(category: ExpenseCategory): Long

    suspend fun getOrCreateCategory(name: String): Long =
        findCategory(name.trim()) ?: insertCategory(ExpenseCategory(name = name.trim()))

    suspend fun seedDefaultCategories() {
        DEFAULT_EXPENSE_CATEGORIES.forEach { getOrCreateCategory(it) }
    }

    @Insert
    suspend fun insertExpense(expense: Expense): Long

    @Query(
        "UPDATE expenses SET categoryId = :categoryId, amount = :amount, expenseDate = :expenseDate, " +
            "description = :description, academicYearId = :academicYearId, classId = :classId WHERE id = :id",
    )
    suspend fun updateExpense(
        id: Long, categoryId: Long, amount: Double, expenseDate: String, description: String,
        academicYearId: Long?, classId: Long?,
    )

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: Long)

    @Query(
        """
        SELECT e.id as id, ec.name as categoryName, e.amount as amount, e.expenseDate as expenseDate,
               e.description as description, ay.yearLabel as yearLabel, c2.className as className,
               e.childId as childId, '' as childName
        FROM expenses e
        JOIN expense_categories ec ON ec.id = e.categoryId
        LEFT JOIN academic_years ay ON ay.id = e.academicYearId
        LEFT JOIN classes c2 ON c2.id = e.classId
        WHERE e.childId = :childId
        ORDER BY e.expenseDate DESC, e.id DESC
        """,
    )
    fun observeForChild(childId: Long): Flow<List<ExpenseRow>>

    /** Every child's expenses combined — shown while no specific child is
     * selected, so opening the Expenses tab never silently displays just
     * whichever child happened to be selected last time. */
    @Query(
        """
        SELECT e.id as id, ec.name as categoryName, e.amount as amount, e.expenseDate as expenseDate,
               e.description as description, ay.yearLabel as yearLabel, c2.className as className,
               ch.fullName as childName, e.childId as childId
        FROM expenses e
        JOIN expense_categories ec ON ec.id = e.categoryId
        JOIN children ch ON ch.id = e.childId
        LEFT JOIN academic_years ay ON ay.id = e.academicYearId
        LEFT JOIN classes c2 ON c2.id = e.classId
        ORDER BY e.expenseDate DESC, e.id DESC
        """,
    )
    fun observeAll(): Flow<List<ExpenseRow>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE childId = :childId")
    fun observeTotalForChild(childId: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses")
    fun observeTotalAll(): Flow<Double>

    @Query(
        """
        SELECT ec.name as categoryName, COALESCE(SUM(e.amount), 0) as total
        FROM expense_categories ec
        LEFT JOIN expenses e ON e.categoryId = ec.id AND e.childId = :childId
        GROUP BY ec.id
        HAVING total > 0
        ORDER BY total DESC
        """,
    )
    suspend fun totalByCategory(childId: Long): List<CategoryTotal>

    @Query(
        """
        SELECT COALESCE(ay.yearLabel, 'Unassigned') as yearLabel, SUM(e.amount) as total
        FROM expenses e
        LEFT JOIN academic_years ay ON ay.id = e.academicYearId
        WHERE e.childId = :childId
        GROUP BY yearLabel
        ORDER BY yearLabel
        """,
    )
    suspend fun totalByYear(childId: Long): List<YearTotal>

    @Insert
    suspend fun insertFeeReceipt(receipt: FeeReceipt): Long
}
