package org.familytools.educationtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ocr_history")
data class OcrHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String,
    val sourcePath: String = "",
    val extractedText: String = "",
    val status: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "backups")
data class BackupRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val backupType: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A certificate or class photo attached to a child, segregated by academic
 * year. [category] is "CERTIFICATE" or "CLASS_PHOTO"; [imagePath] is an
 * absolute path into this app's own external-files storage (the source
 * camera/gallery image is copied there so it survives even if the original
 * is deleted from the device gallery). */
@Entity(tableName = "child_documents")
data class ChildDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val academicYear: String,
    val category: String,
    val title: String = "",
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis(),
)
