package org.familytools.educationtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "academic_years")
data class AcademicYear(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val yearLabel: String,
)

@Entity(tableName = "classes")
data class SchoolClass(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val academicYearId: Long,
    val className: String,
    val section: String = "",
)

@Entity(tableName = "terms")
data class Term(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val termName: String,
)

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "exams")
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val termId: Long,
    val examType: String,
    val examDate: String = "",
    val attendanceDaysPresent: Int? = null,
    val attendanceWorkingDays: Int? = null,
    val teacherRemarks: String = "",
)

@Entity(tableName = "marks")
data class Mark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long,
    val subjectId: Long,
    val marksObtained: Double? = null,
    val maxMarks: Double? = null,
    val grade: String = "",
    val percentage: Double? = null,
    val rank: Int? = null,
    val remarks: String = "",
)

/** Flat, joined view of a child's full marks history — mirrors the Python
 * AcademicRepository.get_marks_history query. */
data class MarkHistoryRow(
    val yearLabel: String,
    val className: String,
    val termName: String,
    val examType: String,
    val examDate: String,
    val subjectName: String,
    val marksObtained: Double?,
    val maxMarks: Double?,
    val grade: String,
    val percentage: Double?,
    val rank: Int?,
    val remarks: String,
    val attendanceDaysPresent: Int? = null,
    val attendanceWorkingDays: Int? = null,
    val teacherRemarks: String = "",
)
