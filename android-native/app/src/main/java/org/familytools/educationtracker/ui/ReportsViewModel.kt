package org.familytools.educationtracker.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.familytools.educationtracker.data.AcademicDao
import org.familytools.educationtracker.data.ChildDao
import org.familytools.educationtracker.data.ExpenseDao
import org.familytools.educationtracker.data.Teacher
import org.familytools.educationtracker.data.TeacherDao
import org.familytools.educationtracker.services.ReportService
import java.io.File

class ReportsViewModel(
    private val academicDao: AcademicDao,
    private val expenseDao: ExpenseDao,
    private val teacherDao: TeacherDao,
    childDao: ChildDao,
) : ViewModel() {
    val children: StateFlow<List<org.familytools.educationtracker.data.Child>> = childDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val teachers: StateFlow<List<Teacher>> = teacherDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun generateAcademicPdf(context: Context, childId: Long, childName: String, onDone: (File) -> Unit) {
        viewModelScope.launch {
            val list = academicDao.observeMarksHistory(childId).first()
            onDone(ReportService.academicSummaryPdf(context, childName, list))
        }
    }

    fun generateExpenseCsv(context: Context, childId: Long, childName: String, onDone: (File) -> Unit) {
        viewModelScope.launch {
            val rows = expenseDao.observeForChild(childId).first()
            onDone(ReportService.expenseReportCsv(context, childName, rows))
        }
    }

    fun generateTeacherPdf(context: Context, teacherId: Long, teacherName: String, onDone: (File) -> Unit) {
        viewModelScope.launch {
            val rows = teacherDao.effectivenessFor(teacherId)
            onDone(ReportService.teacherEffectivenessPdf(context, teacherName, rows))
        }
    }

    companion object {
        fun factory(academicDao: AcademicDao, expenseDao: ExpenseDao, teacherDao: TeacherDao, childDao: ChildDao) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return ReportsViewModel(academicDao, expenseDao, teacherDao, childDao) as T
                }
            }
    }
}
