"""Tiny helper around KivyMD 1.2's MDSnackbar (the old ``Snackbar(text=...)``
one-liner API was deprecated in favor of composing an MDLabel child)."""
from kivymd.uix.label import MDLabel
from kivymd.uix.snackbar import MDSnackbar


def show_snackbar(message: str) -> None:
    MDSnackbar(MDLabel(text=message)).open()
