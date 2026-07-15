package org.familytools.educationtracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.familytools.educationtracker.data.Child
import org.familytools.educationtracker.data.Teacher

class AppViewModels(
    val child: ChildViewModel,
    val academic: AcademicRecordsViewModel,
    val teacher: TeacherViewModel,
    val expense: ExpenseViewModel,
    val analytics: AnalyticsViewModel,
    val reports: ReportsViewModel,
    val settings: SettingsViewModel,
)

@Composable
fun AppNavigation(viewModels: AppViewModels, startLocked: Boolean) {
    val navController = rememberNavController()
    val children by viewModels.child.children.collectAsState()

    NavHost(navController = navController, startDestination = if (startLocked) "lock" else "dashboard") {
        composable("lock") {
            LockScreen(viewModels.settings, onUnlocked = {
                navController.navigate("dashboard") { popUpTo("lock") { inclusive = true } }
            })
        }
        composable("dashboard") {
            DashboardScreen(
                childViewModel = viewModels.child,
                onNavigate = { route -> navController.navigate(route) },
                onSearch = { navController.navigate("search") },
            )
        }
        composable("children") {
            ChildListScreen(
                childViewModel = viewModels.child,
                onBack = { navController.popBackStack() },
                onAddChild = { navController.navigate("child_form/0") },
                onEditChild = { child -> navController.navigate("child_form/${child.id}") },
            )
        }
        composable(
            "child_form/{childId}",
            arguments = listOf(navArgument("childId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getLong("childId") ?: 0L
            val child = if (childId == 0L) Child() else children.firstOrNull { it.id == childId } ?: Child()
            ChildFormScreen(
                child = child,
                onBack = { navController.popBackStack() },
                onSave = { updated -> viewModels.child.save(updated) { navController.popBackStack() } },
                onDelete = { toDelete -> viewModels.child.delete(toDelete) { navController.popBackStack() } },
            )
        }
        composable("academic_records") {
            AcademicRecordsScreen(viewModels.academic, onBack = { navController.popBackStack() })
        }
        composable("scan_report") {
            ScanReportScreen(viewModels.academic, onBack = { navController.popBackStack() })
        }
        composable("teachers") {
            TeachersScreen(
                viewModels.teacher,
                onBack = { navController.popBackStack() },
                onAddTeacher = { navController.navigate("teacher_form/0") },
                onEditTeacher = { t -> navController.navigate("teacher_form/${t.id}") },
            )
        }
        composable(
            "teacher_form/{teacherId}",
            arguments = listOf(navArgument("teacherId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val teacherId = backStackEntry.arguments?.getLong("teacherId") ?: 0L
            val teachers by viewModels.teacher.teachers.collectAsState()
            val teacher = if (teacherId == 0L) Teacher() else teachers.firstOrNull { it.id == teacherId } ?: Teacher()
            TeacherFormScreen(
                teacher = teacher,
                onBack = { navController.popBackStack() },
                onSave = { updated -> viewModels.teacher.save(updated) { navController.popBackStack() } },
                onDelete = { toDelete -> viewModels.teacher.delete(toDelete) { navController.popBackStack() } },
            )
        }
        composable("expenses") {
            ExpensesScreen(
                viewModels.expense,
                onBack = { navController.popBackStack() },
                onScanReceipt = { navController.navigate("scan_receipt") },
            )
        }
        composable("scan_receipt") {
            ScanReceiptScreen(viewModels.expense, onBack = { navController.popBackStack() })
        }
        composable("analytics") {
            AnalyticsScreen(viewModels.analytics, onBack = { navController.popBackStack() })
        }
        composable("reports") {
            ReportsScreen(viewModels.reports, onBack = { navController.popBackStack() })
        }
        composable("backup") {
            BackupScreen(onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(viewModels.settings, onBack = { navController.popBackStack() })
        }
        composable("search") {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenChild = { id -> navController.navigate("child_form/$id") },
                onOpenTeacher = { id -> navController.navigate("teacher_form/$id") },
            )
        }
    }
}
