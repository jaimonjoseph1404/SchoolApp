package org.familytools.educationtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.familytools.educationtracker.data.AcademicDao
import org.familytools.educationtracker.data.ChildDao
import org.familytools.educationtracker.data.Expense
import org.familytools.educationtracker.data.ExpenseCategory
import org.familytools.educationtracker.data.ExpenseDao
import org.familytools.educationtracker.data.ExpenseRow

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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val expenses: StateFlow<List<ExpenseRow>> = _selectedChildId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else expenseDao.observeForChild(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val total: StateFlow<Double> = _selectedChildId
        .flatMapLatest { id -> if (id == null) flowOf(0.0) else expenseDao.observeTotalForChild(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    init {
        viewModelScope.launch { expenseDao.seedDefaultCategories() }
    }

    fun selectChild(id: Long) { _selectedChildId.value = id }

    fun addExpense(
        categoryName: String, amount: String, date: String, description: String,
        yearLabel: String, receiptPath: String, onDone: () -> Unit, onError: (String) -> Unit,
    ) {
        val childId = _selectedChildId.value
        if (childId == null) { onError("Select a child first"); return }
        if (categoryName.isBlank()) { onError("Category is required"); return }
        val amountValue = amount.toDoubleOrNull()
        if (amountValue == null) { onError("Enter a valid amount"); return }

        viewModelScope.launch {
            val categoryId = expenseDao.getOrCreateCategory(categoryName)
            val yearId = if (yearLabel.isNotBlank()) academicDao.getOrCreateAcademicYear(childId, yearLabel) else null
            expenseDao.insertExpense(
                Expense(
                    childId = childId, academicYearId = yearId, categoryId = categoryId,
                    amount = amountValue, expenseDate = date, description = description, receiptPath = receiptPath,
                ),
            )
            onDone()
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch { expenseDao.deleteExpense(id) }
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
