package org.familytools.educationtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MiscDao {
    @Insert
    suspend fun insertOcrHistory(entry: OcrHistoryEntry): Long

    @Insert
    suspend fun insertBackupRecord(record: BackupRecord): Long

    @Query("SELECT * FROM backups ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastBackup(): BackupRecord?
}
