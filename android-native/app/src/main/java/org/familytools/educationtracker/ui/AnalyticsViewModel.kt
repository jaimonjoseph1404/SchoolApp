package org.familytools.educationtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import org.familytools.educationtracker.data.AcademicDao
import org.familytools.educationtracker.data.ChildDao
import org.familytools.educationtracker.data.ExpenseDao
import org.familytools.educationtracker.data.MarkHistoryRow
import org.familytools.educationtracker.services.AnalyticsEngine

class AnalyticsViewModel(
    private val academicDao: AcademicDao,
    expenseDao: ExpenseDao,
    childDao: ChildDao,
) : ViewModel() {
    val engine = AnalyticsEngine(academicDao, expenseDao)
    val children: StateFlow<List<org.familytools.educationtracker.data.Child>> = childDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedChildId = MutableStateFlow<Long?>(null)
    val selectedChildId: StateFlow<Long?> = _selectedChildId

    @OptIn(ExperimentalCoroutinesApi::class)
    val marksHistory: StateFlow<List<MarkHistoryRow>> = _selectedChildId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else academicDao.observeMarksHistory(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectChild(id: Long) { _selectedChildId.value = id }

    companion object {
        fun factory(academicDao: AcademicDao, expenseDao: ExpenseDao, childDao: ChildDao) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return AnalyticsViewModel(academicDao, expenseDao, childDao) as T
                }
            }
    }
}
