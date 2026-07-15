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
