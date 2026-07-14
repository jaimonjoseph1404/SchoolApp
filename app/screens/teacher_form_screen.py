from kivymd.app import MDApp
from kivymd.uix.screen import MDScreen

from app.repositories.teacher_repository import Teacher, TeacherRepository

FIELD_TO_ATTR = {
    "name_field": "name",
    "subject_field": "subject",
    "qualification_field": "qualification",
    "experience_field": "experience",
    "phone_field": "phone",
    "email_field": "email",
    "school_name_field": "school_name",
    "notes_field": "notes",
}


class TeacherFormScreen(MDScreen):
    teacher_id = None

    def load_teacher(self, teacher_id):
        self.teacher_id = teacher_id
        repo = TeacherRepository()
        teacher = repo.get(teacher_id) if teacher_id else Teacher()

        for field_id, attr in FIELD_TO_ATTR.items():
            widget = self.ids.get(field_id)
            if widget is not None:
                widget.text = getattr(teacher, attr, "") or ""

        self.ids.name_field.error = False
        self.ids.name_field.helper_text = ""
        self.ids.title_bar.title = "Edit Teacher" if teacher_id else "Add Teacher"
        self.ids.delete_button.opacity = 1 if teacher_id else 0
        self.ids.delete_button.disabled = not bool(teacher_id)

    def save(self):
        name = self.ids.name_field.text.strip()
        if not name:
            self.ids.name_field.error = True
            self.ids.name_field.helper_text = "Name is required"
            return

        teacher = Teacher(id=self.teacher_id)
        for field_id, attr in FIELD_TO_ATTR.items():
            widget = self.ids.get(field_id)
            setattr(teacher, attr, widget.text.strip() if widget else "")

        repo = TeacherRepository()
        if self.teacher_id:
            repo.update(teacher)
        else:
            self.teacher_id = repo.create(teacher)

        self.go_back()

    def delete(self):
        if not self.teacher_id:
            return
        TeacherRepository().delete(self.teacher_id)
        self.teacher_id = None
        self.go_back()

    def go_back(self):
        MDApp.get_running_app().go_to_screen("teachers")
