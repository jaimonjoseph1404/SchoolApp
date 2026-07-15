package org.familytools.educationtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "children")
data class Child(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // Personal information
    val fullName: String = "",
    val gender: String = "",
    val dateOfBirth: String = "",
    val schoolName: String = "",
    val admissionNumber: String = "",
    val currentClass: String = "",
    val section: String = "",
    val academicYear: String = "",
    val photoPath: String = "",

    // Optional information
    val bloodGroup: String = "",
    val parentNotes: String = "",
    val medicalNotes: String = "",
    val interests: String = "",
    val careerAspiration: String = "",
)
