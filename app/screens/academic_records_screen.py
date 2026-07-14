from kivymd.uix.list import TwoLineListItem
from kivymd.uix.screen import MDScreen

from app.repositories.academic_repository import AcademicRepository
from app.widgets.child_selector import ChildDropdownField
from app.widgets.marks_table import MarksTable
from app.widgets.snackbar import show_snackbar


class AcademicRecordsScreen(MDScreen):
    def on_kv_post(self, base_widget):
        self._selected_child_id = None

        self.child_field = ChildDropdownField(on_child_selected=self._on_child_selected)
        self.ids.child_field_container.add_widget(self.child_field)

        self.marks_table = MarksTable()
        self.ids.marks_table_container.add_widget(self.marks_table)
        self.marks_table.add_row()

    def add_blank_row(self):
        self.marks_table.add_row()

    def _on_child_selected(self, child_id):
        self._selected_child_id = child_id
        self._refresh_history()

    def _refresh_history(self):
        self.ids.history_list.clear_widgets()
        if not self._selected_child_id:
            return
        rows = AcademicRepository().get_marks_history(self._selected_child_id)
        for r in list(reversed(rows))[:30]:
            pct_text = f" ({r['percentage']}%)" if r["percentage"] is not None else ""
            item = TwoLineListItem(
                text=f"{r['subject_name']}: {r['marks_obtained']}/{r['max_marks']}{pct_text}",
                secondary_text=f"{r['year_label']} · {r['class_name']} · {r['term_name']} · {r['exam_type']}",
            )
            self.ids.history_list.add_widget(item)

    def save(self):
        if not self._selected_child_id:
            show_snackbar("Please select a child first")
            return

        year_label = self.ids.year_field.text.strip()
        class_name = self.ids.class_field.text.strip()
        section = self.ids.section_field.text.strip()
        term_name = self.ids.term_field.text.strip()
        exam_type = self.ids.exam_type_field.text.strip()
        exam_date = self.ids.exam_date_field.text.strip()

        if not (year_label and class_name and term_name and exam_type):
            show_snackbar("Academic Year, Class, Term and Exam Type are required")
            return

        rows = self.marks_table.rows_as_dicts()
        if not rows:
            show_snackbar("Add at least one subject with marks")
            return

        repo = AcademicRepository()
        year_id = repo.get_or_create_academic_year(self._selected_child_id, year_label)
        class_id = repo.get_or_create_class(year_id, class_name, section)
        term_id = repo.get_or_create_term(class_id, term_name)
        exam_id = repo.get_or_create_exam(term_id, exam_type, exam_date)

        for row in rows:
            repo.add_or_update_mark(
                exam_id, row["subject"],
                float(row["marks_obtained"]) if row["marks_obtained"] else None,
                float(row["max_marks"]) if row["max_marks"] else None,
                grade=row["grade"],
                rank=int(row["rank"]) if row["rank"] else None,
                remarks=row["remarks"],
            )

        self.marks_table.clear_rows()
        self.marks_table.add_row()
        self._refresh_history()
        show_snackbar("Marks saved")
