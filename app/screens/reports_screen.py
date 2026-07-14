from kivymd.uix.menu import MDDropdownMenu
from kivymd.uix.screen import MDScreen
from kivymd.uix.textfield import MDTextField

from app.repositories.teacher_repository import TeacherRepository
from app.services import report_service
from app.widgets.child_selector import ChildDropdownField
from app.widgets.snackbar import show_snackbar


class TeacherDropdownField(MDTextField):
    def __init__(self, **kwargs):
        kwargs.setdefault("hint_text", "Teacher")
        kwargs.setdefault("readonly", True)
        super().__init__(**kwargs)
        self.selected_teacher_id = None
        self._menu = None
        self.bind(focus=self._on_focus)

    def _on_focus(self, instance, focused):
        if focused:
            self.open_menu()

    def open_menu(self):
        teachers = TeacherRepository().list_all()
        if not teachers:
            self.hint_text = "No teachers yet — add one first"
            return
        items = [{"text": t.name, "on_release": lambda t=t: self._select(t)} for t in teachers]
        self._menu = MDDropdownMenu(caller=self, items=items, width_mult=4)
        self._menu.open()

    def _select(self, teacher):
        self.text = teacher.name
        self.selected_teacher_id = teacher.id
        if self._menu:
            self._menu.dismiss()


class ReportsScreen(MDScreen):
    def on_kv_post(self, base_widget):
        self._selected_child_id = None
        self.child_field = ChildDropdownField(on_child_selected=self._on_child_selected)
        self.ids.child_field_container.add_widget(self.child_field)

        self.teacher_field = TeacherDropdownField()
        self.ids.teacher_field_container.add_widget(self.teacher_field)

    def _on_child_selected(self, child_id):
        self._selected_child_id = child_id

    def generate_academic_pdf(self):
        if not self._selected_child_id:
            show_snackbar("Select a child first")
            return
        try:
            path = report_service.academic_summary_pdf(self._selected_child_id)
        except RuntimeError as exc:
            show_snackbar(str(exc))
            return
        show_snackbar(f"Saved: {path.name}")

    def generate_expense_csv(self):
        if not self._selected_child_id:
            show_snackbar("Select a child first")
            return
        path = report_service.expense_report_csv(self._selected_child_id)
        show_snackbar(f"Saved: {path.name}")

    def generate_expense_excel(self):
        if not self._selected_child_id:
            show_snackbar("Select a child first")
            return
        try:
            path = report_service.expense_report_excel(self._selected_child_id)
        except RuntimeError as exc:
            show_snackbar(str(exc))
            return
        show_snackbar(f"Saved: {path.name}")

    def generate_teacher_pdf(self):
        if not self.teacher_field.selected_teacher_id:
            show_snackbar("Select a teacher first")
            return
        try:
            path = report_service.teacher_effectiveness_pdf(self.teacher_field.selected_teacher_id)
        except RuntimeError as exc:
            show_snackbar(str(exc))
            return
        show_snackbar(f"Saved: {path.name}")
