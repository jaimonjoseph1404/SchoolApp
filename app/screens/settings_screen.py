from kivymd.app import MDApp
from kivymd.uix.button import MDFlatButton
from kivymd.uix.dialog import MDDialog
from kivymd.uix.screen import MDScreen
from kivymd.uix.textfield import MDTextField

from app.repositories.settings_repository import SettingsRepository
from app.widgets.snackbar import show_snackbar


class SettingsScreen(MDScreen):
    def on_pre_enter(self, *args):
        self.refresh()

    def refresh(self):
        repo = SettingsRepository()
        self.ids.dark_mode_switch.active = repo.get_theme_style() == "Dark"
        self.ids.pin_switch.active = repo.is_pin_lock_enabled()
        self.ids.change_pin_button.disabled = not repo.is_pin_lock_enabled()

    def on_theme_toggle(self, active):
        style = "Dark" if active else "Light"
        app = MDApp.get_running_app()
        app.theme_cls.theme_style = style
        SettingsRepository().set_theme_style(style)

    def on_pin_toggle(self, active):
        if active:
            self._prompt_set_pin()
        else:
            SettingsRepository().disable_pin()
            show_snackbar("PIN lock disabled")
        self.ids.change_pin_button.disabled = not active

    def change_pin(self):
        self._prompt_set_pin()

    def _prompt_set_pin(self):
        field = MDTextField(hint_text="New 4-6 digit PIN", password=True, input_filter="int")
        dialog = MDDialog(
            title="Set PIN",
            type="custom",
            content_cls=field,
            buttons=[
                MDFlatButton(text="CANCEL", on_release=lambda *_a: self._cancel_pin_dialog(dialog)),
                MDFlatButton(text="SAVE", on_release=lambda *_a: self._save_pin(dialog, field)),
            ],
        )
        dialog.open()

    def _cancel_pin_dialog(self, dialog):
        dialog.dismiss()
        self.refresh()

    def _save_pin(self, dialog, field):
        pin = field.text.strip()
        dialog.dismiss()
        if len(pin) < 4:
            show_snackbar("PIN must be at least 4 digits")
            self.refresh()
            return
        SettingsRepository().set_pin(pin)
        show_snackbar("PIN saved")
        self.refresh()
