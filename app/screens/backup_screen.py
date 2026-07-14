from pathlib import Path

from kivymd.uix.button import MDFlatButton
from kivymd.uix.dialog import MDDialog
from kivymd.uix.screen import MDScreen
from kivymd.uix.textfield import MDTextField

from app.services import backup_service
from app.widgets.snackbar import show_snackbar


class BackupScreen(MDScreen):
    def on_pre_enter(self, *args):
        self.refresh_status()

    def refresh_status(self):
        last = backup_service.last_backup()
        if last:
            self.ids.status_label.text = f"Last backup: {last['backup_type'].upper()} at {last['created_at']}"
        else:
            self.ids.status_label.text = "No backups yet."

    def export_json(self):
        path = backup_service.export_json()
        self.refresh_status()
        show_snackbar(f"Saved: {path.name}")

    def export_zip(self):
        path = backup_service.export_zip()
        self.refresh_status()
        show_snackbar(f"Saved: {path.name}")

    def export_encrypted(self):
        self._prompt_password("Set a backup password", self._do_export_encrypted)

    def _do_export_encrypted(self, password):
        path = backup_service.export_encrypted(password)
        self.refresh_status()
        show_snackbar(f"Encrypted backup saved: {path.name}")

    def import_backup(self):
        try:
            from plyer import filechooser

            filechooser.open_file(
                on_selection=self._on_file_selected,
                filters=[("Backups", "*.json", "*.zip", "*.bak")],
            )
        except Exception:
            show_snackbar("File picker unavailable on this platform")

    def _on_file_selected(self, selection):
        if not selection:
            return
        path = Path(selection[0])
        try:
            if path.suffix == ".json":
                backup_service.import_json(path)
                show_snackbar("Restored from JSON backup")
            elif path.suffix == ".zip":
                backup_service.import_zip(path)
                show_snackbar("Restored from ZIP backup")
            elif path.suffix == ".bak":
                self._prompt_password(
                    "Enter backup password", lambda pw: self._do_import_encrypted(path, pw)
                )
            else:
                show_snackbar("Unrecognized backup file type")
        except Exception as exc:
            show_snackbar(f"Restore failed: {exc}")

    def _do_import_encrypted(self, path, password):
        try:
            backup_service.import_encrypted(password, path)
            show_snackbar("Restored from encrypted backup")
        except ValueError as exc:
            show_snackbar(str(exc))

    def _prompt_password(self, title, on_confirm):
        field = MDTextField(hint_text="Password", password=True)
        dialog = MDDialog(
            title=title,
            type="custom",
            content_cls=field,
            buttons=[
                MDFlatButton(text="CANCEL", on_release=lambda *_a: dialog.dismiss()),
                MDFlatButton(
                    text="CONFIRM",
                    on_release=lambda *_a: self._confirm_password(dialog, field, on_confirm),
                ),
            ],
        )
        dialog.open()

    def _confirm_password(self, dialog, field, on_confirm):
        password = field.text.strip()
        dialog.dismiss()
        if not password:
            show_snackbar("Password cannot be empty")
            return
        on_confirm(password)

    def share_email(self):
        last = backup_service.last_backup()
        if not last:
            show_snackbar("Create a backup first")
            return
        try:
            from plyer import email

            email.send(
                subject="Education Tracker Backup",
                text="Please find the attached backup file.",
                create_chooser=True,
                attachments=[last["file_path"]],
            )
        except Exception:
            show_snackbar(f"Email sharing unavailable — backup is at {last['file_path']}")
