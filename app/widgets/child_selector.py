"""Reusable "pick a child" dropdown field, used across most modules."""
from __future__ import annotations

from kivymd.uix.menu import MDDropdownMenu
from kivymd.uix.textfield import MDTextField

from app.repositories.child_repository import ChildRepository


class ChildDropdownField(MDTextField):
    def __init__(self, on_child_selected=None, **kwargs):
        kwargs.setdefault("hint_text", "Child")
        kwargs.setdefault("readonly", True)
        super().__init__(**kwargs)
        self.selected_child_id = None
        self._on_child_selected = on_child_selected
        self._menu = None
        self.bind(focus=self._on_focus)

    def _on_focus(self, instance, focused):
        if focused:
            self.open_menu()

    def open_menu(self):
        children = ChildRepository().list_all()
        if not children:
            self.hint_text = "No children yet — add one first"
            return
        items = [
            {"text": c.full_name or f"Child #{c.id}", "on_release": lambda c=c: self._select(c)}
            for c in children
        ]
        self._menu = MDDropdownMenu(caller=self, items=items, width_mult=4)
        self._menu.open()

    def select_child_id(self, child_id):
        for c in ChildRepository().list_all():
            if c.id == child_id:
                self._select(c)
                return

    def _select(self, child):
        self.text = child.full_name or f"Child #{child.id}"
        self.selected_child_id = child.id
        if self._menu:
            self._menu.dismiss()
        if self._on_child_selected:
            self._on_child_selected(child.id)
