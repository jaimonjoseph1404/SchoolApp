import json

from kivymd.uix.screen import MDScreen

from app.core.database import get_db
from app.repositories.academic_repository import AcademicRepository
from app.services import ocr_service
from app.widgets.child_selector import ChildDropdownField
from app.widgets.marks_table import MarksTable
from app.widgets.snackbar import show_snackbar


class ScanReportScreen(MDScreen):
    def on_kv_post(self, base_widget):
        self._selected_child_id = None
        self._last_raw_text = ""
        self._last_source_path = ""

        self.child_field = ChildDropdownField(on_child_selected=self._on_child_selected)
        self.ids.child_field_container.add_widget(self.child_field)

        self.marks_table = MarksTable()
        self.ids.marks_table_container.add_widget(self.marks_table)
        self.marks_table.add_row()

        if not ocr_service.is_ocr_available():
            self.ids.ocr_status_label.text = ocr_service.ocr_unavailable_message()

    def _on_child_selected(self, child_id):
        self._selected_child_id = child_id

    def add_blank_row(self):
        self.marks_table.add_row()

    def capture_camera(self):
        try:
            from plyer import camera

            camera.take_picture(filename="capture.jpg", on_complete=self._on_image_captured)
        except Exception:
            show_snackbar("Camera capture isn't available on this device — use Gallery or Upload instead.")

    def _on_image_captured(self, path):
        if path:
            self._run_ocr_on_image(path)

    def pick_gallery_image(self):
        try:
            from plyer import filechooser

            filechooser.open_file(
                on_selection=self._on_image_selected,
                filters=[("Images", "*.jpg", "*.jpeg", "*.png")],
            )
        except Exception:
            show_snackbar("File picker unavailable on this platform")

    def _on_image_selected(self, selection):
        if selection:
            self._run_ocr_on_image(selection[0])

    def pick_pdf(self):
        try:
            from plyer import filechooser

            filechooser.open_file(on_selection=self._on_pdf_selected, filters=[("PDF", "*.pdf")])
        except Exception:
            show_snackbar("File picker unavailable on this platform")

    def _on_pdf_selected(self, selection):
        if selection:
            self._run_ocr_on_pdf(selection[0])

    def _run_ocr_on_image(self, path):
        self._last_source_path = path
        if not ocr_service.is_ocr_available():
            self.ids.ocr_status_label.text = ocr_service.ocr_unavailable_message()
            return
        self.ids.ocr_status_label.text = "Processing image..."
        try:
            text = ocr_service.extract_text_from_image(path)
        except Exception as exc:
            self.ids.ocr_status_label.text = f"OCR failed: {exc}"
            return
        self._apply_extracted_text(text)

    def _run_ocr_on_pdf(self, path):
        self._last_source_path = path
        self.ids.ocr_status_label.text = "Processing PDF..."
        try:
            text = ocr_service.extract_text_from_pdf(path)
        except Exception as exc:
            self.ids.ocr_status_label.text = f"OCR failed: {exc}"
            return
        self._apply_extracted_text(text)

    def _apply_extracted_text(self, text):
        self._last_raw_text = text
        rows = ocr_service.parse_report_text(text)
        if rows:
            self.marks_table.load_extracted(rows)
            self.ids.ocr_status_label.text = f"Extracted {len(rows)} subject row(s) — please verify."
        else:
            self.ids.ocr_status_label.text = "Couldn't automatically parse rows — please enter marks manually."

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

        self._record_ocr_history()

        self.marks_table.clear_rows()
        self.marks_table.add_row()
        self.ids.ocr_status_label.text = ""
        show_snackbar("Report saved")

    def _record_ocr_history(self):
        conn = get_db()
        conn.execute(
            "INSERT INTO ocr_history (source_type, source_path, extracted_json, status) VALUES (?, ?, ?, ?)",
            ("report", self._last_source_path, json.dumps(self._last_raw_text), "saved"),
        )
        conn.commit()
