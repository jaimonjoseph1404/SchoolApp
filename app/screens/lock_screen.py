from kivymd.app import MDApp
from kivymd.uix.screen import MDScreen

from app.repositories.settings_repository import SettingsRepository


class LockScreen(MDScreen):
    def unlock(self):
        pin = self.ids.pin_field.text.strip()
        app = MDApp.get_running_app()
        if SettingsRepository().verify_pin(pin):
            self.ids.pin_field.text = ""
            self.ids.error_label.text = ""
            app.locked = False
            app.go_to_screen("dashboard")
        else:
            self.ids.error_label.text = "Incorrect PIN"
            self.ids.pin_field.text = ""
