package org.familytools.educationtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.familytools.educationtracker.data.AcademicDao
import org.familytools.educationtracker.data.AssignmentRow
import org.familytools.educationtracker.data.ChildDao
import org.familytools.educationtracker.data.Teacher
import org.familytools.educationtracker.data.TeacherDao

class TeacherViewModel(
    private val teacherDao: TeacherDao,
    private val childDao: ChildDao,
    private val academicDao: AcademicDao,
) : ViewModel() {
    val teachers: StateFlow<List<Teacher>> = teacherDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val assignments: StateFlow<List<AssignmentRow>> = teacherDao.observeAssignments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val children: StateFlow<List<org.familytools.educationtracker.data.Child>> = childDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editing = MutableStateFlow<Teacher?>(null)
    val editing: StateFlow<Teacher?> = _editing

    fun startNew() { _editing.value = Teacher() }
    fun startEditing(teacher: Teacher) { _editing.value = teacher }
    fun stopEditing() { _editing.value = null }

    fun save(teacher: Teacher, onDone: () -> Unit) {
        viewModelScope.launch { teacherDao.save(teacher); onDone() }
    }

    fun delete(teacher: Teacher, onDone: () -> Unit) {
        viewModelScope.launch { teacherDao.delete(teacher); onDone() }
    }

    fun deleteAssignment(id: Long) {
        viewModelScope.launch { teacherDao.deleteAssignment(id) }
    }

    fun assign(
        childId: Long, yearLabel: String, className: String, subjectName: String, teacherId: Long,
        onDone: () -> Unit, onError: (String) -> Unit,
    ) {
        if (yearLabel.isBlank() || className.isBlank() || subjectName.isBlank()) {
            onError("All fields are required")
            return
        }
        viewModelScope.launch {
            val yearId = academicDao.getOrCreateAcademicYear(childId, yearLabel)
            val classId = academicDao.getOrCreateClass(yearId, className)
            val subjectId = academicDao.getOrCreateSubject(subjectName)
            teacherDao.assign(childId, yearId, classId, subjectId, teacherId)
            onDone()
        }
    }

    companion object {
        fun factory(teacherDao: TeacherDao, childDao: ChildDao, academicDao: AcademicDao) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return TeacherViewModel(teacherDao, childDao, academicDao) as T
                }
            }
    }
}
