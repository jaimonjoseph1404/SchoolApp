"""Reusable editable subject/marks table — used by both manual Academic
Records entry and the Scan Report OCR verification step, so the parent
corrects OCR results using the exact same widget they'd use to type marks
in by hand (PRD section 7 step 6)."""
from __future__ import annotations

from kivymd.uix.boxlayout import MDBoxLayout
from kivymd.uix.button import MDIconButton
from kivymd.uix.textfield import MDTextField


class MarkRowWidget(MDBoxLayout):
    def __init__(self, on_delete=None, **kwargs):
        kwargs.setdefault("orientation", "horizontal")
        kwargs.setdefault("spacing", "4dp")
        kwargs.setdefault("adaptive_height", True)
        super().__init__(**kwargs)

        self.subject_field = MDTextField(hint_text="Subject", size_hint_x=2)
        self.obtained_field = MDTextField(hint_text="Marks", input_filter="float", size_hint_x=1)
        self.max_field = MDTextField(hint_text="Max", input_filter="float", size_hint_x=1)
        self.grade_field = MDTextField(hint_text="Grade", size_hint_x=1)
        self.rank_field = MDTextField(hint_text="Rank", input_filter="int", size_hint_x=1)
        self.remarks_field = MDTextField(hint_text="Remarks", size_hint_x=2)
        delete_btn = MDIconButton(icon="close", size_hint_x=None, width="40dp")
        if on_delete:
            delete_btn.bind(on_release=lambda *_a: on_delete(self))

        for widget in (
            self.subject_field, self.obtained_field, self.max_field,
            self.grade_field, self.rank_field, self.remarks_field, delete_btn,
        ):
            self.add_widget(widget)

    def to_dict(self) -> dict:
        return {
            "subject": self.subject_field.text.strip(),
            "marks_obtained": self.obtained_field.text.strip(),
            "max_marks": self.max_field.text.strip(),
            "grade": self.grade_field.text.strip(),
            "rank": self.rank_field.text.strip(),
            "remarks": self.remarks_field.text.strip(),
        }


class MarksTable(MDBoxLayout):
    def __init__(self, **kwargs):
        kwargs.setdefault("orientation", "vertical")
        kwargs.setdefault("spacing", "4dp")
        kwargs.setdefault("adaptive_height", True)
        super().__init__(**kwargs)
        self._rows: list[MarkRowWidget] = []

    def add_row(self, subject="", marks_obtained="", max_marks="", grade="", rank="", remarks=""):
        row = MarkRowWidget(on_delete=self.remove_row)
        row.subject_field.text = str(subject or "")
        row.obtained_field.text = "" if marks_obtained in (None, "") else str(marks_obtained)
        row.max_field.text = "" if max_marks in (None, "") else str(max_marks)
        row.grade_field.text = str(grade or "")
        row.rank_field.text = "" if rank in (None, "") else str(rank)
        row.remarks_field.text = str(remarks or "")
        self._rows.append(row)
        self.add_widget(row)
        return row

    def remove_row(self, row: MarkRowWidget):
        if row in self._rows:
            self._rows.remove(row)
            self.remove_widget(row)

    def clear_rows(self):
        for row in list(self._rows):
            self.remove_row(row)

    def load_extracted(self, extracted_rows):
        self.clear_rows()
        for r in extracted_rows:
            self.add_row(r.subject, r.marks_obtained, r.max_marks, r.grade, r.rank, r.remarks)

    def load_marks(self, marks):
        self.clear_rows()
        for m in marks:
            self.add_row(m.subject_name, m.marks_obtained, m.max_marks, m.grade, m.rank, m.remarks)

    def rows_as_dicts(self) -> list[dict]:
        return [d for r in self._rows if (d := r.to_dict())["subject"]]
