package org.familytools.educationtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.familytools.educationtracker.data.AcademicDao
import org.familytools.educationtracker.data.ChildDao
import org.familytools.educationtracker.data.Expense
import org.familytools.educationtracker.data.ExpenseCategory
import org.familytools.educationtracker.data.ExpenseDao
import org.familytools.educationtracker.data.ExpenseRow
import org.familytools.educationtracker.data.FeeReceipt
import org.familytools.educationtracker.services.ExtractedReceipt

class ExpenseViewModel(
    private val expenseDao: ExpenseDao,
    private val childDao: ChildDao,
    private val academicDao: AcademicDao,
) : ViewModel() {
    val children: StateFlow<List<org.familytools.educationtracker.data.Child>> = childDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories: StateFlow<List<ExpenseCategory>> = expenseDao.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedChildId = MutableStateFlow<Long?>(null)
    val selectedChildId: StateFlow<Long?> = _selectedChildId

    // No child selected -> every child's expenses combined (observeAll), not
    // an empty list — a fresh visit to the Expenses tab previously kept
    // showing whichever child was selected last time (the ViewModel outlives
    // the composable), which looked like "only one child's data" even though
    // the dropdown itself displayed blank.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val expenses: StateFlow<List<ExpenseRow>> = _selectedChildId
        .flatMapLatest { id -> if (id == null) expenseDao.observeAll() else expenseDao.observeForChild(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val total: StateFlow<Double> = _selectedChildId
        .flatMapLatest { id -> if (id == null) expenseDao.observeTotalAll() else expenseDao.observeTotalForChild(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    init {
        viewModelScope.launch { expenseDao.seedDefaultCategories() }
    }

    fun selectChild(id: Long) { _selectedChildId.value = id }

    fun clearSelectedChild() { _selectedChildId.value = null }

    private suspend fun resolveClassId(childId: Long, yearLabel: String, className: String): Long? {
        if (className.isBlank()) return null
        val yearId = if (yearLabel.isNotBlank()) academicDao.getOrCreateAcademicYear(childId, yearLabel) else return null
        return academicDao.getOrCreateClass(yearId, className)
    }

    fun addExpense(
        categoryName: String, amount: String, date: String, description: String,
        yearLabel: String, receiptPath: String, className: String = "",
        onDone: () -> Unit, onError: (String) -> Unit,
    ) {
        val childId = _selectedChildId.value
        if (childId == null) { onError("Select a child first"); return }
        if (categoryName.isBlank()) { onError("Category is required"); return }
        val amountValue = amount.toDoubleOrNull()
        if (amountValue == null) { onError("Enter a valid amount"); return }

        viewModelScope.launch {
            val categoryId = expenseDao.getOrCreateCategory(categoryName)
            val yearId = if (yearLabel.isNotBlank()) academicDao.getOrCreateAcademicYear(childId, yearLabel) else null
            val classId = resolveClassId(childId, yearLabel, className)
            expenseDao.insertExpense(
                Expense(
                    childId = childId, academicYearId = yearId, classId = classId, categoryId = categoryId,
                    amount = amountValue, expenseDate = date, description = description, receiptPath = receiptPath,
                ),
            )
            onDone()
        }
    }

    fun updateExpense(
        id: Long, childId: Long, categoryName: String, amount: String, date: String, description: String,
        yearLabel: String, className: String = "",
        onDone: () -> Unit, onError: (String) -> Unit,
    ) {
        if (categoryName.isBlank()) { onError("Category is required"); return }
        val amountValue = amount.toDoubleOrNull()
        if (amountValue == null) { onError("Enter a valid amount"); return }

        viewModelScope.launch {
            val categoryId = expenseDao.getOrCreateCategory(categoryName)
            val yearId = if (yearLabel.isNotBlank()) academicDao.getOrCreateAcademicYear(childId, yearLabel) else null
            val classId = resolveClassId(childId, yearLabel, className)
            expenseDao.updateExpense(id, categoryId, amountValue, date, description, yearId, classId)
            onDone()
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch { expenseDao.deleteExpense(id) }
    }

    /** Saves an expense + linked [FeeReceipt] straight from OCR output — the
     * scanned-receipt counterpart of [addExpense], with no manual re-typing
     * of amount/category/date required when the OCR parse succeeded. */
    fun addExpenseFromReceipt(
        childId: Long, category: String, receipt: ExtractedReceipt, imagePath: String,
        onDone: () -> Unit, onError: (String) -> Unit,
    ) {
        val amount = receipt.totalAmount
        if (amount == null) { onError("Couldn't read an amount — enter it manually"); return }
        viewModelScope.launch {
            val categoryId = expenseDao.getOrCreateCategory(category.ifBlank { "Miscellaneous" })
            val expenseId = expenseDao.insertExpense(
                Expense(
                    childId = childId, categoryId = categoryId, amount = amount,
                    expenseDate = receipt.receiptDate, description = receipt.schoolName, receiptPath = imagePath,
                ),
            )
            expenseDao.insertFeeReceipt(
                FeeReceipt(
                    expenseId = expenseId, schoolName = receipt.schoolName, receiptNumber = receipt.receiptNumber,
                    receiptDate = receipt.receiptDate, amount = amount, totalAmount = amount, imagePath = imagePath,
                ),
            )
            onDone()
        }
    }

    companion object {
        fun factory(expenseDao: ExpenseDao, childDao: ChildDao, academicDao: AcademicDao) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return ExpenseViewModel(expenseDao, childDao, academicDao) as T
                }
            }
    }
}
