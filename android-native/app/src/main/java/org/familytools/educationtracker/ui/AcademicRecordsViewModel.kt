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
import org.familytools.educationtracker.data.Child
import org.familytools.educationtracker.data.ChildDao
import org.familytools.educationtracker.data.MarkHistoryRow
import org.familytools.educationtracker.services.NameMatcher

class AcademicRecordsViewModel(
    private val childDao: ChildDao,
    private val academicDao: AcademicDao,
) : ViewModel() {
    val children: StateFlow<List<Child>> = childDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedChildId = MutableStateFlow<Long?>(null)
    val selectedChildId: StateFlow<Long?> = _selectedChildId

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val marksHistory: StateFlow<List<MarkHistoryRow>> = _selectedChildId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else academicDao.observeMarksHistory(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectChild(id: Long) {
        _selectedChildId.value = id
    }

    /** Finds an enrolled child by (fuzzy) name match, or creates one on the
     * spot — lets a progress-report scan onboard a brand-new child without
     * a separate "Add Child" trip when the OCR'd name doesn't match anyone
     * already enrolled. */
    suspend fun findOrCreateChildByName(
        name: String, schoolName: String, admissionNumber: String,
        currentClass: String, section: String, academicYear: String,
    ): Child {
        NameMatcher.findBestMatch(children.value, name)?.let { return it }
        val id = childDao.upsert(
            Child(
                fullName = name.trim(), schoolName = schoolName, admissionNumber = admissionNumber,
                currentClass = currentClass, section = section, academicYear = academicYear,
            ),
        )
        return childDao.getById(id) ?: error("Failed to create child")
    }

    fun saveExam(
        yearLabel: String, className: String, section: String, termName: String,
        examType: String, examDate: String, rows: List<MarkFormRow>,
        coCurricularRows: List<MarkFormRow> = emptyList(),
        attendanceDaysPresent: Int? = null, attendanceWorkingDays: Int? = null, teacherRemarks: String = "",
        force: Boolean = false,
        onDone: () -> Unit, onError: (String) -> Unit, onDuplicate: () -> Unit = {},
    ) {
        val childId = _selectedChildId.value
        if (childId == null) {
            onError("Please select a child first")
            return
        }
        if (yearLabel.isBlank() || className.isBlank() || termName.isBlank() || examType.isBlank()) {
            onError("Academic Year, Class, Term and Exam Type are required")
            return
        }
        val validRows = rows.filter { it.subject.isNotBlank() }
        val validCoCurricular = coCurricularRows.filter { it.subject.isNotBlank() }
        if (validRows.isEmpty() && validCoCurricular.isEmpty()) {
            onError("Add at least one subject with marks")
            return
        }
        viewModelScope.launch {
            if (!force) {
                val existingExamId = academicDao.findExamExact(
                    childId, yearLabel.trim(), className.trim(), section.trim(), termName.trim(), examType.trim(),
                )
                if (existingExamId != null && academicDao.markCountForExam(existingExamId) > 0) {
                    onDuplicate()
                    return@launch
                }
            }
            val yearId = academicDao.getOrCreateAcademicYear(childId, yearLabel)
            val classId = academicDao.getOrCreateClass(yearId, className, section)
            val termId = academicDao.getOrCreateTerm(classId, termName)
            val examId = academicDao.getOrCreateExam(
                termId, examType, examDate, attendanceDaysPresent, attendanceWorkingDays, teacherRemarks,
            )
            for (row in validRows + validCoCurricular) {
                academicDao.addOrUpdateMark(
                    examId, row.subject,
                    row.marksObtained.toDoubleOrNull(), row.maxMarks.toDoubleOrNull(),
                    row.grade, row.rank.toIntOrNull(), row.remarks,
                )
            }
            onDone()
        }
    }

    companion object {
        fun factory(childDao: ChildDao, academicDao: AcademicDao) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return AcademicRecordsViewModel(childDao, academicDao) as T
            }
        }
    }
}
