package org.familytools.educationtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildDao {
    @Query("SELECT * FROM children ORDER BY fullName COLLATE NOCASE")
    fun observeAll(): Flow<List<Child>>

    @Query("SELECT * FROM children WHERE id = :childId")
    suspend fun getById(childId: Long): Child?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(child: Child): Long

    @Update
    suspend fun update(child: Child)

    @Delete
    suspend fun delete(child: Child)
}
