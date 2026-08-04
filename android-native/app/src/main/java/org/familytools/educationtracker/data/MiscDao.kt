package org.familytools.educationtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MiscDao {
    @Insert
    suspend fun insertOcrHistory(entry: OcrHistoryEntry): Long

    @Insert
    suspend fun insertBackupRecord(record: BackupRecord): Long

    @Query("SELECT * FROM backups ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastBackup(): BackupRecord?

    @Insert
    suspend fun insertChildDocument(document: ChildDocument): Long

    @Query("SELECT * FROM child_documents WHERE childId = :childId ORDER BY academicYear DESC, createdAt DESC")
    fun observeDocumentsForChild(childId: Long): Flow<List<ChildDocument>>

    @Query("DELETE FROM child_documents WHERE id = :id")
    suspend fun deleteChildDocument(id: Long)
}
