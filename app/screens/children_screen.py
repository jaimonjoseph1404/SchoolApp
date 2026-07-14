from kivymd.app import MDApp
from kivymd.uix.list import IconLeftWidget, TwoLineIconListItem
from kivymd.uix.screen import MDScreen

from app.repositories.child_repository import ChildRepository


class ChildrenListScreen(MDScreen):
    def on_pre_enter(self, *args):
        self.refresh()

    def refresh(self):
        self.ids.children_list.clear_widgets()
        children = ChildRepository().list_all()
        self.ids.empty_label.opacity = 0 if children else 1
        for child in children:
            subtitle_parts = [p for p in (child.current_class, child.school_name) if p]
            item = TwoLineIconListItem(
                text=child.full_name or "Unnamed",
                secondary_text=" | ".join(subtitle_parts) if subtitle_parts else "No class/school set",
            )
            item.bind(on_release=lambda inst, cid=child.id: self.open_child(cid))
            item.add_widget(IconLeftWidget(icon="account"))
            self.ids.children_list.add_widget(item)

    def open_child(self, child_id):
        MDApp.get_running_app().edit_child(child_id)

    def add_child(self):
        MDApp.get_running_app().edit_child(None)
