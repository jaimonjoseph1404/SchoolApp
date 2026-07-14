from pathlib import Path

from kivy.lang import Builder
from kivymd.app import MDApp
from kivymd.uix.button import MDFlatButton
from kivymd.uix.dialog import MDDialog
from kivymd.uix.list import IconLeftWidget, OneLineIconListItem

from app.core.database import get_db
from app.repositories.settings_repository import SettingsRepository
from app.screens.academic_records_screen import AcademicRecordsScreen
from app.screens.analytics_screen import AnalyticsScreen
from app.screens.backup_screen import BackupScreen
from app.screens.child_form_screen import ChildFormScreen
from app.screens.children_screen import ChildrenListScreen
from app.screens.dashboard_screen import DashboardScreen
from app.screens.expense_form_screen import ExpenseFormScreen
from app.screens.expenses_screen import ExpensesScreen
from app.screens.lock_screen import LockScreen
from app.screens.reports_screen import ReportsScreen
from app.screens.scan_report_screen import ScanReportScreen
from app.screens.search_screen import SearchScreen
from app.screens.settings_screen import SettingsScreen
from app.screens.teacher_form_screen import TeacherFormScreen
from app.screens.teachers_screen import TeachersScreen

KV_DIR = Path(__file__).parent / "screens" / "kv"

NAV_ITEMS = [
    ("view-dashboard", "Dashboard", "dashboard"),
    ("account-child", "Children", "children_list"),
    ("book-open-page-variant", "Academic Records", "academic_records"),
    ("camera", "Scan Report", "scan_report"),
    ("cash-multiple", "Expenses", "expenses"),
    ("account-tie", "Teachers", "teachers"),
    ("chart-line", "Analytics", "analytics"),
    ("file-chart", "Reports", "reports"),
    ("cloud-upload", "Backup", "backup"),
    ("cog", "Settings", "settings"),
]

ROOT_KV = """
MDNavigationLayout:
    MDScreenManager:
        id: screen_manager

    MDNavigationDrawer:
        id: nav_drawer
        radius: (0, 16, 16, 0)

        MDBoxLayout:
            orientation: "vertical"
            padding: "8dp"
            spacing: "8dp"

            MDLabel:
                text: "Education Tracker"
                font_style: "H6"
                adaptive_height: True
                padding: ("8dp", "16dp")

            ScrollView:
                MDList:
                    id: drawer_list
"""


class SchoolApp(MDApp):
    locked = False

    def build(self):
        self.title = "Education Performance & Cost Tracker"
        self.theme_cls.primary_palette = "Teal"

        get_db()
        settings = SettingsRepository()
        self.theme_cls.theme_style = settings.get_theme_style()

        for kv_file in sorted(KV_DIR.glob("*.kv")):
            Builder.load_file(str(kv_file))

        root = Builder.load_string(ROOT_KV)
        self.screen_manager = root.ids.screen_manager
        self.nav_drawer = root.ids.nav_drawer

        self.screen_manager.add_widget(DashboardScreen())
        self.screen_manager.add_widget(ChildrenListScreen())
        self.screen_manager.add_widget(ChildFormScreen())
        self.screen_manager.add_widget(AcademicRecordsScreen())
        self.screen_manager.add_widget(TeachersScreen())
        self.screen_manager.add_widget(TeacherFormScreen())
        self.screen_manager.add_widget(ExpensesScreen())
        self.screen_manager.add_widget(ExpenseFormScreen())
        self.screen_manager.add_widget(ScanReportScreen())
        self.screen_manager.add_widget(AnalyticsScreen())
        self.screen_manager.add_widget(ReportsScreen())
        self.screen_manager.add_widget(BackupScreen())
        self.screen_manager.add_widget(SettingsScreen())
        self.screen_manager.add_widget(SearchScreen())
        self.screen_manager.add_widget(LockScreen())

        self._populate_drawer(root.ids.drawer_list)

        if settings.is_pin_lock_enabled():
            self.locked = True
            self.screen_manager.current = "lock"
        else:
            self.screen_manager.current = "dashboard"

        return root

    def _populate_drawer(self, drawer_list):
        for icon, text, screen_name in NAV_ITEMS:
            item = OneLineIconListItem(text=text)
            item.bind(on_release=lambda inst, s=screen_name: self.go_to_screen(s))
            item.add_widget(IconLeftWidget(icon=icon))
            drawer_list.add_widget(item)

    def open_nav_drawer(self):
        if self.locked:
            return
        self.nav_drawer.set_state("open")

    def go_to_screen(self, screen_name: str):
        if self.locked and screen_name != "lock":
            return
        self.screen_manager.current = screen_name
        self.nav_drawer.set_state("close")

    def edit_child(self, child_id=None):
        form = self.screen_manager.get_screen("child_form")
        form.load_child(child_id)
        self.screen_manager.current = "child_form"

    def edit_teacher(self, teacher_id=None):
        form = self.screen_manager.get_screen("teacher_form")
        form.load_teacher(teacher_id)
        self.screen_manager.current = "teacher_form"

    def show_notifications(self):
        from app.services.notification_service import get_notifications

        notifications = get_notifications()
        text = "\n\n".join(notifications) if notifications else "No notifications."
        dialog = MDDialog(
            title="Notifications",
            text=text,
            buttons=[MDFlatButton(text="CLOSE", on_release=lambda *_a: dialog.dismiss())],
        )
        dialog.open()
