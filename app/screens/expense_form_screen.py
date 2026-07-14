from kivymd.app import MDApp
from kivymd.uix.menu import MDDropdownMenu
from kivymd.uix.pickers import MDDatePicker
from kivymd.uix.screen import MDScreen

from app.repositories.academic_repository import AcademicRepository
from app.repositories.expense_repository import ExpenseRepository
from app.widgets.snackbar import show_snackbar


class ExpenseFormScreen(MDScreen):
    child_id = None
    _active_menu = None

    def open_for_child(self, child_id, child_name):
        self.child_id = child_id
        self.ids.child_label.text = f"For: {child_name}"
        for field_id in ("category_field", "amount_field", "date_field", "year_field",
                          "description_field", "receipt_field"):
            self.ids[field_id].text = ""
        self.ids.amount_field.error = False
        self.ids.amount_field.helper_text = ""

    def open_category_menu(self):
        categories = ExpenseRepository().list_categories()
        items = [
            {"text": c["name"], "on_release": lambda name=c["name"]: self._select_category(name)}
            for c in categories
        ]
        self._active_menu = MDDropdownMenu(caller=self.ids.category_field, items=items, width_mult=4)
        self._active_menu.open()

    def _select_category(self, name):
        self.ids.category_field.text = name
        if self._active_menu:
            self._active_menu.dismiss()

    def open_date_picker(self):
        picker = MDDatePicker()
        picker.bind(on_save=self._on_date_selected)
        picker.open()

    def _on_date_selected(self, instance, value, date_range):
        self.ids.date_field.text = value.strftime("%Y-%m-%d")

    def browse_receipt(self):
        try:
            from plyer import filechooser

            filechooser.open_file(
                on_selection=self._on_receipt_selected,
                filters=[("Images", "*.jpg", "*.jpeg", "*.png"), ("PDF", "*.pdf")],
            )
        except Exception:
            pass

    def _on_receipt_selected(self, selection):
        if selection:
            self.ids.receipt_field.text = selection[0]

    def save(self):
        category = self.ids.category_field.text.strip()
        amount_text = self.ids.amount_field.text.strip()
        if not category:
            show_snackbar("Category is required")
            return
        try:
            amount = float(amount_text)
        except ValueError:
            self.ids.amount_field.error = True
            self.ids.amount_field.helper_text = "Enter a valid amount"
            return

        year_id = None
        year_label = self.ids.year_field.text.strip()
        if year_label:
            year_id = AcademicRepository().get_or_create_academic_year(self.child_id, year_label)

        ExpenseRepository().create(
            self.child_id, category, amount,
            expense_date=self.ids.date_field.text.strip(),
            description=self.ids.description_field.text.strip(),
            receipt_path=self.ids.receipt_field.text.strip(),
            academic_year_id=year_id,
        )
        self.go_back()

    def go_back(self):
        MDApp.get_running_app().go_to_screen("expenses")
