from kivymd.app import MDApp
from kivymd.uix.list import IconLeftWidget, TwoLineIconListItem
from kivymd.uix.screen import MDScreen

from app.services.search_service import search_all

_ICONS = {
    "Child": "account-child",
    "Subject": "book-open-page-variant",
    "Teacher": "account-tie",
    "Academic Year": "calendar-range",
    "School": "school",
    "Expense Category": "cash-multiple",
}


class SearchScreen(MDScreen):
    def on_search_text(self, query):
        self.ids.results_list.clear_widgets()
        for r in search_all(query):
            secondary = r.category + (f" · {r.detail}" if r.detail else "")
            item = TwoLineIconListItem(text=r.label, secondary_text=secondary)
            item.add_widget(IconLeftWidget(icon=_ICONS.get(r.category, "magnify")))
            if r.category in ("Child", "Teacher") and r.ref_id:
                item.bind(on_release=lambda inst, cat=r.category, rid=r.ref_id: self._open(cat, rid))
            self.ids.results_list.add_widget(item)

    def _open(self, category, ref_id):
        app = MDApp.get_running_app()
        if category == "Child":
            app.edit_child(ref_id)
        elif category == "Teacher":
            app.edit_teacher(ref_id)

    def go_back(self):
        MDApp.get_running_app().go_to_screen("dashboard")
