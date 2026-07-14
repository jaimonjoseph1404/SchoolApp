from kivymd.app import MDApp
from kivymd.uix.boxlayout import MDBoxLayout
from kivymd.uix.button import MDFlatButton
from kivymd.uix.dialog import MDDialog
from kivymd.uix.list import IconLeftWidget, IconRightWidget, TwoLineAvatarIconListItem, TwoLineIconListItem
from kivymd.uix.menu import MDDropdownMenu
from kivymd.uix.screen import MDScreen
from kivymd.uix.textfield import MDTextField

from app.repositories.academic_repository import AcademicRepository
from app.repositories.teacher_repository import TeacherRepository
from app.widgets.child_selector import ChildDropdownField
from app.widgets.snackbar import show_snackbar


class AssignmentDialogContent(MDBoxLayout):
    def __init__(self, **kwargs):
        kwargs.setdefault("orientation", "vertical")
        kwargs.setdefault("spacing", "10dp")
        kwargs.setdefault("size_hint_y", None)
        kwargs.setdefault("height", "280dp")
        super().__init__(**kwargs)

        self._teacher_id = None
        self._teacher_menu = None

        self.child_field = ChildDropdownField()
        self.year_field = MDTextField(hint_text="Academic Year *")
        self.class_field = MDTextField(hint_text="Class *")
        self.subject_field = MDTextField(hint_text="Subject *")
        self.teacher_field = MDTextField(hint_text="Teacher *", readonly=True)
        self.teacher_field.bind(focus=self._on_teacher_focus)

        for widget in (
            self.child_field, self.year_field, self.class_field,
            self.subject_field, self.teacher_field,
        ):
            self.add_widget(widget)

    def _on_teacher_focus(self, instance, focused):
        if not focused:
            return
        teachers = TeacherRepository().list_all()
        if not teachers:
            show_snackbar("Add a teacher first")
            return
        items = [{"text": t.name, "on_release": lambda t=t: self._select_teacher(t)} for t in teachers]
        self._teacher_menu = MDDropdownMenu(caller=self.teacher_field, items=items, width_mult=4)
        self._teacher_menu.open()

    def _select_teacher(self, teacher):
        self.teacher_field.text = teacher.name
        self._teacher_id = teacher.id
        if self._teacher_menu:
            self._teacher_menu.dismiss()

    def save(self) -> bool:
        child_id = self.child_field.selected_child_id
        year_label = self.year_field.text.strip()
        class_name = self.class_field.text.strip()
        subject_name = self.subject_field.text.strip()

        if not (child_id and year_label and class_name and subject_name and self._teacher_id):
            show_snackbar("All fields are required")
            return False

        academic = AcademicRepository()
        year_id = academic.get_or_create_academic_year(child_id, year_label)
        class_id = academic.get_or_create_class(year_id, class_name)
        subject_id = academic.get_or_create_subject(subject_name)

        TeacherRepository().assign(child_id, year_id, class_id, subject_id, self._teacher_id)
        return True


class TeachersScreen(MDScreen):
    def on_pre_enter(self, *args):
        self.refresh()

    def refresh(self):
        self.refresh_teachers()
        self.refresh_assignments()

    def refresh_teachers(self):
        self.ids.teacher_list.clear_widgets()
        teachers = TeacherRepository().list_all()
        self.ids.empty_label.opacity = 0 if teachers else 1
        for t in teachers:
            item = TwoLineIconListItem(text=t.name, secondary_text=t.subject or "No subject set")
            item.bind(on_release=lambda inst, tid=t.id: self.open_teacher(tid))
            item.add_widget(IconLeftWidget(icon="account-tie"))
            self.ids.teacher_list.add_widget(item)

    def refresh_assignments(self):
        self.ids.assignment_list.clear_widgets()
        for a in TeacherRepository().list_assignments():
            item = TwoLineAvatarIconListItem(
                text=f"{a.teacher_name} → {a.subject_name}",
                secondary_text=f"{a.child_name} · {a.year_label} · {a.class_name}",
            )
            delete_icon = IconRightWidget(icon="delete-outline")
            delete_icon.bind(on_release=lambda inst, aid=a.id: self.delete_assignment(aid))
            item.add_widget(delete_icon)
            self.ids.assignment_list.add_widget(item)

    def delete_assignment(self, assignment_id):
        TeacherRepository().delete_assignment(assignment_id)
        self.refresh_assignments()
        show_snackbar("Assignment removed")

    def open_teacher(self, teacher_id):
        MDApp.get_running_app().edit_teacher(teacher_id)

    def add_teacher(self):
        MDApp.get_running_app().edit_teacher(None)

    def open_assign_dialog(self):
        content = AssignmentDialogContent()
        dialog = MDDialog(
            title="Assign Teacher",
            type="custom",
            content_cls=content,
            buttons=[
                MDFlatButton(text="CANCEL", on_release=lambda *_a: dialog.dismiss()),
                MDFlatButton(text="SAVE", on_release=lambda *_a: self._save_assignment(dialog, content)),
            ],
        )
        dialog.open()

    def _save_assignment(self, dialog, content):
        if content.save():
            dialog.dismiss()
            self.refresh_assignments()
            show_snackbar("Teacher assigned")
