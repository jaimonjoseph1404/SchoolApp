package org.familytools.educationtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Child::class, AcademicYear::class, SchoolClass::class, Term::class, Subject::class,
        Exam::class, Mark::class, Teacher::class, TeacherAssignment::class, ExpenseCategory::class,
        Expense::class, FeeReceipt::class, OcrHistoryEntry::class, BackupRecord::class,
    ],
    version = 2,
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

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "educationtracker.db",
                )
                    // Pre-release schema; destructive migration is fine until there's real user data.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
