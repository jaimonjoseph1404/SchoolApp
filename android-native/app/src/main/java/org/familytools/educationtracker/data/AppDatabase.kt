package org.familytools.educationtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Child::class, AcademicYear::class, SchoolClass::class, Term::class, Subject::class,
        Exam::class, Mark::class, Teacher::class, TeacherAssignment::class, ExpenseCategory::class,
        Expense::class, FeeReceipt::class, OcrHistoryEntry::class, BackupRecord::class,
        SubjectTemplateItem::class, ChildDocument::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao
    abstract fun academicDao(): AcademicDao
    abstract fun teacherDao(): TeacherDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun miscDao(): MiscDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // v3 -> v4 only adds a new table (subject/co-curricular templates) —
        // an explicit migration keeps existing children/marks/exams intact,
        // now that real report data has actually been scanned and saved.
        // (fallbackToDestructiveMigration below remains as a safety net for
        // any future bump that doesn't get an explicit migration.)
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subject_templates` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`childId` INTEGER NOT NULL, `className` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `itemName` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL)",
                )
            }
        }

        // v4 -> v5 adds the child_documents table (certificates + class
        // photos, year-segregated) — additive, existing data untouched.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `child_documents` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`childId` INTEGER NOT NULL, `academicYear` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`imagePath` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
            }
        }

        // v5 -> v6 adds the Total (marks obtained / max marks across all
        // subjects) columns on exams — previously discarded as noise by the
        // OCR parser, now captured and shown for verification before save.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exams ADD COLUMN totalMarksObtained REAL")
                db.execSQL("ALTER TABLE exams ADD COLUMN totalMaxMarks REAL")
            }
        }

        // v6 -> v7 merges duplicate Subject rows that only differ by spacing/
        // dashes/case (e.g. "English - I", "English -I", "English I" — three
        // separate OCR/typed variants of one real subject, which fragmented
        // that subject's marks across three rows and showed as three
        // separate one-point "trends" instead of one real trend). Keeps the
        // lowest id per normalized group as canonical, reassigns every
        // mark/template row pointing at a duplicate onto that canonical id,
        // then drops the now-empty duplicates. Uses only GROUP BY/subqueries
        // (no window functions) since the bundled SQLite version varies by
        // device/API level and window-function support isn't guaranteed.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TEMP TABLE subject_canonical AS " +
                        "SELECT REPLACE(REPLACE(UPPER(name), ' ', ''), '-', '') AS norm_key, MIN(id) AS canonical_id " +
                        "FROM subjects GROUP BY norm_key",
                )
                db.execSQL(
                    "UPDATE marks SET subjectId = (" +
                        "  SELECT sc.canonical_id FROM subjects s " +
                        "  JOIN subject_canonical sc ON sc.norm_key = REPLACE(REPLACE(UPPER(s.name), ' ', ''), '-', '') " +
                        "  WHERE s.id = marks.subjectId" +
                        ") WHERE subjectId IN (" +
                        "  SELECT s.id FROM subjects s " +
                        "  JOIN subject_canonical sc ON sc.norm_key = REPLACE(REPLACE(UPPER(s.name), ' ', ''), '-', '') " +
                        "  WHERE s.id != sc.canonical_id" +
                        ")",
                )
                db.execSQL("DELETE FROM subjects WHERE id NOT IN (SELECT canonical_id FROM subject_canonical)")
                db.execSQL("DROP TABLE subject_canonical")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "educationtracker.db",
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    // Pre-release schema; destructive migration is fine until there's real user data.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
