from kivymd.app import MDApp
from kivymd.uix.list import IconRightWidget, TwoLineAvatarIconListItem
from kivymd.uix.screen import MDScreen

from app.repositories.expense_repository import ExpenseRepository
from app.widgets.child_selector import ChildDropdownField
from app.widgets.snackbar import show_snackbar


class ExpensesScreen(MDScreen):
    def on_kv_post(self, base_widget):
        self._selected_child_id = None
        self._selected_child_name = ""
        self.child_field = ChildDropdownField(on_child_selected=self._on_child_selected)
        self.ids.child_field_container.add_widget(self.child_field)

    def on_pre_enter(self, *args):
        if self._selected_child_id:
            self.refresh()

    def _on_child_selected(self, child_id):
        self._selected_child_id = child_id
        self._selected_child_name = self.child_field.text
        self.refresh()

    def refresh(self):
        self.ids.expense_list.clear_widgets()
        if not self._selected_child_id:
            self.ids.total_label.text = "Select a child to view expenses"
            return
        repo = ExpenseRepository()
        expenses = repo.list_for_child(self._selected_child_id)
        total = repo.total_for_child(self._selected_child_id)
        self.ids.total_label.text = f"Total spent: Rs. {total:,.2f}"
        for e in expenses:
            details = "  ·  ".join(p for p in (e.expense_date, e.description) if p)
            item = TwoLineAvatarIconListItem(
                text=f"{e.category_name} — Rs. {e.amount:,.2f}",
                secondary_text=details or "No details",
            )
            delete_icon = IconRightWidget(icon="delete-outline")
            delete_icon.bind(on_release=lambda inst, eid=e.id: self.delete_expense(eid))
            item.add_widget(delete_icon)
            self.ids.expense_list.add_widget(item)

    def delete_expense(self, expense_id):
        ExpenseRepository().delete(expense_id)
        self.refresh()
        show_snackbar("Expense deleted")

    def add_expense(self):
        if not self._selected_child_id:
            show_snackbar("Select a child first")
            return
        app = MDApp.get_running_app()
        form = app.screen_manager.get_screen("expense_form")
        form.open_for_child(self._selected_child_id, self._selected_child_name)
        app.go_to_screen("expense_form")
