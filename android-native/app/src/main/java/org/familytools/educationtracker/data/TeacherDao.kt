package org.familytools.educationtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TeacherDao {
    @Query("SELECT * FROM teachers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Teacher>>

    @Query("SELECT * FROM teachers WHERE id = :id")
    suspend fun getById(id: Long): Teacher?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(teacher: Teacher): Long

    @Update
    suspend fun update(teacher: Teacher)

    @Delete
    suspend fun delete(teacher: Teacher)

    suspend fun save(teacher: Teacher) {
        if (teacher.id == 0L) upsert(teacher) else update(teacher)
    }

    @Query(
        "SELECT id FROM teacher_assignments WHERE childId = :childId AND academicYearId = :yearId " +
            "AND classId = :classId AND subjectId = :subjectId LIMIT 1",
    )
    suspend fun findAssignment(childId: Long, yearId: Long, classId: Long, subjectId: Long): Long?

    @Insert
    suspend fun insertAssignment(assignment: TeacherAssignment): Long

    @Query("UPDATE teacher_assignments SET teacherId = :teacherId WHERE id = :id")
    suspend fun updateAssignmentTeacher(id: Long, teacherId: Long)

    suspend fun assign(childId: Long, yearId: Long, classId: Long, subjectId: Long, teacherId: Long) {
        val existing = findAssignment(childId, yearId, classId, subjectId)
        if (existing != null) {
            updateAssignmentTeacher(existing, teacherId)
        } else {
            insertAssignment(
                TeacherAssignment(
                    childId = childId, academicYearId = yearId, classId = classId,
                    subjectId = subjectId, teacherId = teacherId,
                ),
            )
        }
    }

    @Query(
        """
        SELECT ta.id as id, ch.fullName as childName, ay.yearLabel as yearLabel,
               c.className as className, s.name as subjectName, t.name as teacherName
        FROM teacher_assignments ta
        JOIN children ch ON ch.id = ta.childId
        JOIN academic_years ay ON ay.id = ta.academicYearId
        JOIN classes c ON c.id = ta.classId
        JOIN subjects s ON s.id = ta.subjectId
        JOIN teachers t ON t.id = ta.teacherId
        ORDER BY ay.yearLabel DESC, ch.fullName
        """,
    )
    fun observeAssignments(): Flow<List<AssignmentRow>>

    @Query("DELETE FROM teacher_assignments WHERE id = :id")
    suspend fun deleteAssignment(id: Long)

    @Query(
        """
        SELECT ay.yearLabel as yearLabel, AVG(m.percentage) as avgPercentage, COUNT(*) as markCount
        FROM teacher_assignments ta
        JOIN academic_years ay ON ay.id = ta.academicYearId
        JOIN classes c ON c.id = ta.classId
        JOIN terms t ON t.classId = c.id
        JOIN exams e ON e.termId = t.id
        JOIN marks m ON m.examId = e.id AND m.subjectId = ta.subjectId
        WHERE ta.teacherId = :teacherId
        GROUP BY ay.yearLabel
        ORDER BY ay.yearLabel
        """,
    )
    suspend fun effectivenessFor(teacherId: Long): List<TeacherEffectivenessRow>
}
