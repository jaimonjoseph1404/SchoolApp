from kivymd.uix.screen import MDScreen

from app.repositories.child_repository import ChildRepository


class DashboardScreen(MDScreen):
    def on_pre_enter(self, *args):
        self.refresh_summary()

    def refresh_summary(self):
        count = len(ChildRepository().list_all())
        label = self.ids.get("children_count_label")
        if label is not None:
            label.text = str(count)
