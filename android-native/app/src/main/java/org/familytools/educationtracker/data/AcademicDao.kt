package org.familytools.educationtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AcademicDao {
    // --- Academic Years ---
    @Query("SELECT id FROM academic_years WHERE childId = :childId AND yearLabel = :yearLabel LIMIT 1")
    suspend fun findAcademicYear(childId: Long, yearLabel: String): Long?

    @Insert
    suspend fun insertAcademicYear(year: AcademicYear): Long

    suspend fun getOrCreateAcademicYear(childId: Long, yearLabel: String): Long =
        findAcademicYear(childId, yearLabel.trim())
            ?: insertAcademicYear(AcademicYear(childId = childId, yearLabel = yearLabel.trim()))

    // --- Classes ---
    @Query(
        "SELECT id FROM classes WHERE academicYearId = :yearId AND className = :className " +
            "AND section = :section LIMIT 1",
    )
    suspend fun findClass(yearId: Long, className: String, section: String): Long?

    @Insert
    suspend fun insertClass(schoolClass: SchoolClass): Long

    suspend fun getOrCreateClass(yearId: Long, className: String, section: String = ""): Long =
        findClass(yearId, className.trim(), section.trim())
            ?: insertClass(SchoolClass(academicYearId = yearId, className = className.trim(), section = section.trim()))

    // --- Terms ---
    @Query("SELECT id FROM terms WHERE classId = :classId AND termName = :termName LIMIT 1")
    suspend fun findTerm(classId: Long, termName: String): Long?

    @Insert
    suspend fun insertTerm(term: Term): Long

    suspend fun getOrCreateTerm(classId: Long, termName: String): Long =
        findTerm(classId, termName.trim()) ?: insertTerm(Term(classId = classId, termName = termName.trim()))

    // --- Subjects ---
    @Query("SELECT id FROM subjects WHERE name = :name LIMIT 1")
    suspend fun findSubject(name: String): Long?

    @Insert
    suspend fun insertSubject(subject: Subject): Long

    suspend fun getOrCreateSubject(name: String): Long =
        findSubject(name.trim()) ?: insertSubject(Subject(name = name.trim()))

    @Query("SELECT DISTINCT s.name FROM subjects s " +
        "JOIN marks m ON m.subjectId = s.id " +
        "JOIN exams e ON e.id = m.examId " +
        "JOIN terms t ON t.id = e.termId " +
        "JOIN classes c ON c.id = t.classId " +
        "JOIN academic_years ay ON ay.id = c.academicYearId " +
        "WHERE ay.childId = :childId ORDER BY s.name")
    suspend fun subjectsForChild(childId: Long): List<String>

    // --- Exams ---
    @Query("SELECT id FROM exams WHERE termId = :termId AND examType = :examType LIMIT 1")
    suspend fun findExam(termId: Long, examType: String): Long?

    @Insert
    suspend fun insertExam(exam: Exam): Long

    @Query(
        "UPDATE exams SET examDate = :examDate, attendanceDaysPresent = :attendanceDaysPresent, " +
            "attendanceWorkingDays = :attendanceWorkingDays, teacherRemarks = :teacherRemarks WHERE id = :id",
    )
    suspend fun updateExamDetails(
        id: Long, examDate: String, attendanceDaysPresent: Int?, attendanceWorkingDays: Int?, teacherRemarks: String,
    )

    suspend fun getOrCreateExam(
        termId: Long, examType: String, examDate: String = "",
        attendanceDaysPresent: Int? = null, attendanceWorkingDays: Int? = null, teacherRemarks: String = "",
    ): Long {
        val existing = findExam(termId, examType.trim())
        return if (existing != null) {
            updateExamDetails(existing, examDate.trim(), attendanceDaysPresent, attendanceWorkingDays, teacherRemarks.trim())
            existing
        } else {
            insertExam(
                Exam(
                    termId = termId, examType = examType.trim(), examDate = examDate.trim(),
                    attendanceDaysPresent = attendanceDaysPresent, attendanceWorkingDays = attendanceWorkingDays,
                    teacherRemarks = teacherRemarks.trim(),
                ),
            )
        }
    }

    /** Pure lookup (no creation) across the full child->year->class->term->exam
     * hierarchy by literal label values — used to detect "this report was
     * already scanned" before [getOrCreateExam] would otherwise silently
     * create-or-reuse the row. */
    @Query(
        """
        SELECT e.id FROM exams e
        JOIN terms t ON t.id = e.termId
        JOIN classes c ON c.id = t.classId
        JOIN academic_years ay ON ay.id = c.academicYearId
        WHERE ay.childId = :childId AND ay.yearLabel = :yearLabel AND c.className = :className
          AND c.section = :section AND t.termName = :termName AND e.examType = :examType
        LIMIT 1
        """,
    )
    suspend fun findExamExact(
        childId: Long, yearLabel: String, className: String, section: String, termName: String, examType: String,
    ): Long?

    @Query("SELECT COUNT(*) FROM marks WHERE examId = :examId")
    suspend fun markCountForExam(examId: Long): Int

    // --- Marks ---
    @Query("SELECT id FROM marks WHERE examId = :examId AND subjectId = :subjectId LIMIT 1")
    suspend fun findMark(examId: Long, subjectId: Long): Long?

    @Insert
    suspend fun insertMark(mark: Mark): Long

    @Query(
        "UPDATE marks SET marksObtained = :marksObtained, maxMarks = :maxMarks, grade = :grade, " +
            "percentage = :percentage, rank = :rank, remarks = :remarks WHERE id = :id",
    )
    suspend fun updateMark(
        id: Long, marksObtained: Double?, maxMarks: Double?, grade: String,
        percentage: Double?, rank: Int?, remarks: String,
    )

    suspend fun addOrUpdateMark(
        examId: Long, subjectName: String, marksObtained: Double?, maxMarks: Double?,
        grade: String = "", rank: Int? = null, remarks: String = "",
    ) {
        val subjectId = getOrCreateSubject(subjectName)
        val percentage = if (marksObtained != null && maxMarks != null && maxMarks != 0.0) {
            (marksObtained / maxMarks) * 100.0
        } else null
        val existing = findMark(examId, subjectId)
        if (existing != null) {
            updateMark(existing, marksObtained, maxMarks, grade, percentage, rank, remarks)
        } else {
            insertMark(
                Mark(
                    examId = examId, subjectId = subjectId, marksObtained = marksObtained,
                    maxMarks = maxMarks, grade = grade, percentage = percentage, rank = rank, remarks = remarks,
                ),
            )
        }
    }

    // --- History / aggregate views ---
    @Query(
        """
        SELECT ay.yearLabel as yearLabel, c.className as className, t.termName as termName,
               e.examType as examType, e.examDate as examDate, s.name as subjectName,
               m.marksObtained as marksObtained, m.maxMarks as maxMarks, m.grade as grade,
               m.percentage as percentage, m.rank as rank, m.remarks as remarks,
               e.attendanceDaysPresent as attendanceDaysPresent, e.attendanceWorkingDays as attendanceWorkingDays,
               e.teacherRemarks as teacherRemarks
        FROM marks m
        JOIN exams e ON e.id = m.examId
        JOIN terms t ON t.id = e.termId
        JOIN classes c ON c.id = t.classId
        JOIN academic_years ay ON ay.id = c.academicYearId
        JOIN subjects s ON s.id = m.subjectId
        WHERE ay.childId = :childId
        ORDER BY ay.yearLabel, c.id, t.id, e.id
        """,
    )
    fun observeMarksHistory(childId: Long): Flow<List<MarkHistoryRow>>

    @Query(
        """
        SELECT COUNT(*) FROM marks m
        JOIN exams e ON e.id = m.examId
        JOIN terms t ON t.id = e.termId
        JOIN classes c ON c.id = t.classId
        JOIN academic_years ay ON ay.id = c.academicYearId
        WHERE ay.childId = :childId AND ay.yearLabel = :yearLabel
        """,
    )
    suspend fun markCountForYear(childId: Long, yearLabel: String): Int
}
