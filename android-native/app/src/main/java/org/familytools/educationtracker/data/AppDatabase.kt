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
        SubjectTemplateItem::class,
    ],
    version = 4,
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

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "educationtracker.db",
                )
                    .addMigrations(MIGRATION_3_4)
                    // Pre-release schema; destructive migration is fine until there's real user data.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
