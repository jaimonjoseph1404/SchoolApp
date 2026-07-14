from kivymd.app import MDApp
from kivymd.uix.menu import MDDropdownMenu
from kivymd.uix.pickers import MDDatePicker
from kivymd.uix.screen import MDScreen

from app.repositories.child_repository import Child, ChildRepository

GENDER_OPTIONS = ["Male", "Female", "Other"]
BLOOD_GROUP_OPTIONS = ["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"]

FIELD_TO_ATTR = {
    "full_name_field": "full_name",
    "gender_field": "gender",
    "dob_field": "date_of_birth",
    "school_name_field": "school_name",
    "admission_number_field": "admission_number",
    "current_class_field": "current_class",
    "section_field": "section",
    "academic_year_field": "academic_year",
    "photo_field": "photo_path",
    "blood_group_field": "blood_group",
    "parent_notes_field": "parent_notes",
    "medical_notes_field": "medical_notes",
    "interests_field": "interests",
    "career_aspiration_field": "career_aspiration",
}


class ChildFormScreen(MDScreen):
    child_id = None
    _active_menu = None

    def on_kv_post(self, base_widget):
        self.ids.gender_field.bind(focus=self._on_gender_focus)
        self.ids.blood_group_field.bind(focus=self._on_blood_group_focus)

    def load_child(self, child_id):
        self.child_id = child_id
        repo = ChildRepository()
        child = repo.get(child_id) if child_id else Child()

        for field_id, attr in FIELD_TO_ATTR.items():
            widget = self.ids.get(field_id)
            if widget is not None:
                widget.text = getattr(child, attr, "") or ""

        self.ids.full_name_field.error = False
        self.ids.full_name_field.helper_text = ""
        self.ids.title_bar.title = "Edit Child" if child_id else "Add Child"
        self.ids.delete_button.opacity = 1 if child_id else 0
        self.ids.delete_button.disabled = not bool(child_id)

    def save(self):
        full_name = self.ids.full_name_field.text.strip()
        if not full_name:
            self.ids.full_name_field.error = True
            self.ids.full_name_field.helper_text = "Full name is required"
            return

        child = Child(id=self.child_id)
        for field_id, attr in FIELD_TO_ATTR.items():
            widget = self.ids.get(field_id)
            setattr(child, attr, widget.text.strip() if widget else "")

        repo = ChildRepository()
        if self.child_id:
            repo.update(child)
        else:
            self.child_id = repo.create(child)

        self.go_back()

    def delete(self):
        if not self.child_id:
            return
        ChildRepository().delete(self.child_id)
        self.child_id = None
        self.go_back()

    def go_back(self):
        MDApp.get_running_app().go_to_screen("children_list")

    def open_gender_menu(self):
        self._open_option_menu(self.ids.gender_field, GENDER_OPTIONS)

    def open_blood_group_menu(self):
        self._open_option_menu(self.ids.blood_group_field, BLOOD_GROUP_OPTIONS)

    def _on_gender_focus(self, instance, focused):
        if focused:
            self.open_gender_menu()

    def _on_blood_group_focus(self, instance, focused):
        if focused:
            self.open_blood_group_menu()

    def _open_option_menu(self, caller, options):
        menu_items = [
            {"text": option, "on_release": lambda o=option: self._select_option(caller, o)}
            for option in options
        ]
        self._active_menu = MDDropdownMenu(caller=caller, items=menu_items, width_mult=3)
        self._active_menu.open()

    def _select_option(self, field, value):
        field.text = value
        if self._active_menu is not None:
            self._active_menu.dismiss()

    def open_date_picker(self):
        picker = MDDatePicker()
        picker.bind(on_save=self._on_date_selected)
        picker.open()

    def _on_date_selected(self, instance, value, date_range):
        self.ids.dob_field.text = value.strftime("%Y-%m-%d")

    def browse_photo(self):
        try:
            from plyer import filechooser

            filechooser.open_file(
                on_selection=self._on_photo_selected,
                filters=[("Images", "*.jpg", "*.jpeg", "*.png")],
            )
        except Exception:
            pass

    def _on_photo_selected(self, selection):
        if selection:
            self.ids.photo_field.text = selection[0]
