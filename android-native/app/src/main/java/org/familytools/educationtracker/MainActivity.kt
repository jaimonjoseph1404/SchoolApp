package org.familytools.educationtracker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.familytools.educationtracker.data.AppDatabase
import org.familytools.educationtracker.data.SettingsRepository
import org.familytools.educationtracker.ui.AcademicRecordsViewModel
import org.familytools.educationtracker.ui.AnalyticsViewModel
import org.familytools.educationtracker.ui.AppNavigation
import org.familytools.educationtracker.ui.AppViewModels
import org.familytools.educationtracker.ui.ChildViewModel
import org.familytools.educationtracker.ui.ExpenseViewModel
import org.familytools.educationtracker.ui.ReportsViewModel
import org.familytools.educationtracker.ui.SettingsViewModel
import org.familytools.educationtracker.ui.TeacherViewModel
import org.familytools.educationtracker.ui.theme.EducationTrackerTheme

// FragmentActivity (not plain ComponentActivity) because androidx.biometric's
// BiometricPrompt requires a FragmentActivity host — the native capability
// that motivated moving the Android build off Kivy in the first place.
class MainActivity : FragmentActivity() {

    private val db by lazy { AppDatabase.getInstance(applicationContext) }
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    private val childViewModel: ChildViewModel by viewModels {
        ChildViewModel.factory(org.familytools.educationtracker.data.ChildRepository(db.childDao()))
    }
    private val academicViewModel: AcademicRecordsViewModel by viewModels {
        AcademicRecordsViewModel.factory(db.childDao(), db.academicDao())
    }
    private val teacherViewModel: TeacherViewModel by viewModels {
        TeacherViewModel.factory(db.teacherDao(), db.childDao(), db.academicDao())
    }
    private val expenseViewModel: ExpenseViewModel by viewModels {
        ExpenseViewModel.factory(db.expenseDao(), db.childDao(), db.academicDao())
    }
    private val analyticsViewModel: AnalyticsViewModel by viewModels {
        AnalyticsViewModel.factory(db.academicDao(), db.expenseDao(), db.childDao())
    }
    private val reportsViewModel: ReportsViewModel by viewModels {
        ReportsViewModel.factory(db.academicDao(), db.expenseDao(), db.teacherDao(), db.childDao())
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(settingsRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startLocked = runBlocking { settingsRepository.isPinLockEnabled.first() }

        setContent {
            val isDark by settingsViewModel.isDarkTheme.collectAsState()
            EducationTrackerTheme(darkTheme = isDark) {
                AppNavigation(
                    viewModels = AppViewModels(
                        child = childViewModel,
                        academic = academicViewModel,
                        teacher = teacherViewModel,
                        expense = expenseViewModel,
                        analytics = analyticsViewModel,
                        reports = reportsViewModel,
                        settings = settingsViewModel,
                    ),
                    startLocked = startLocked,
                )
            }
        }
    }
}
