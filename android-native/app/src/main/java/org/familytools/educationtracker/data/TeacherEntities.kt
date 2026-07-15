package org.familytools.educationtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teachers")
data class Teacher(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val subject: String = "",
    val qualification: String = "",
    val experience: String = "",
    val phone: String = "",
    val email: String = "",
    val schoolName: String = "",
    val notes: String = "",
)

@Entity(tableName = "teacher_assignments")
data class TeacherAssignment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val academicYearId: Long,
    val classId: Long,
    val subjectId: Long,
    val teacherId: Long,
)

data class AssignmentRow(
    val id: Long,
    val childName: String,
    val yearLabel: String,
    val className: String,
    val subjectName: String,
    val teacherName: String,
)

data class TeacherEffectivenessRow(
    val yearLabel: String,
    val avgPercentage: Double?,
    val markCount: Int,
)
