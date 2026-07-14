"""Reports Module (PRD section 11) — Academic, Expense and Teacher reports as PDF/Excel/CSV.

PDF generation (reportlab) is imported lazily and guarded: reportlab's
optional C accelerator has no python-for-android recipe and fails to cross-
compile against newer Python (see buildozer.spec), so it isn't in the
Android build's requirements yet. CSV/Excel export (stdlib csv, openpyxl)
are pure Python and always available.
"""
from __future__ import annotations

import csv
from datetime import datetime
from pathlib import Path
from typing import Optional

from openpyxl import Workbook

from app.repositories.academic_repository import AcademicRepository
from app.repositories.child_repository import ChildRepository
from app.repositories.expense_repository import ExpenseRepository
from app.repositories.teacher_repository import TeacherRepository

try:
    import reportlab  # noqa: F401

    PDF_AVAILABLE = True
except Exception:
    PDF_AVAILABLE = False


def _require_pdf():
    if not PDF_AVAILABLE:
        raise RuntimeError("PDF export isn't available on this build (reportlab not installed).")


def reports_dir() -> Path:
    try:
        from kivy.app import App

        running = App.get_running_app()
        if running is not None:
            path = Path(running.user_data_dir) / "reports"
            path.mkdir(parents=True, exist_ok=True)
            return path
    except Exception:
        pass
    path = Path(__file__).resolve().parent.parent / "data" / "reports"
    path.mkdir(parents=True, exist_ok=True)
    return path


def _timestamped_name(prefix: str, ext: str) -> str:
    return f"{prefix}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.{ext}"


def academic_summary_pdf(child_id: int, path: Optional[Path] = None) -> Path:
    _require_pdf()
    from reportlab.lib import colors
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import getSampleStyleSheet
    from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

    child = ChildRepository().get(child_id)
    if child is None:
        raise ValueError(f"No child with id {child_id}")
    rows = AcademicRepository().get_marks_history(child_id)

    path = path or reports_dir() / _timestamped_name(f"academic_summary_{child.full_name}", "pdf")
    styles = getSampleStyleSheet()
    doc = SimpleDocTemplate(str(path), pagesize=A4)
    story = [
        Paragraph(f"Academic Summary — {child.full_name}", styles["Title"]),
        Paragraph(f"School: {child.school_name or '-'}  |  Class: {child.current_class or '-'}", styles["Normal"]),
        Spacer(1, 12),
    ]

    table_data = [["Year", "Class", "Term", "Exam", "Subject", "Marks", "Max", "%", "Grade", "Rank"]]
    for r in rows:
        table_data.append([
            r["year_label"], r["class_name"], r["term_name"], r["exam_type"], r["subject_name"],
            r["marks_obtained"], r["max_marks"],
            f"{r['percentage']:.1f}" if r["percentage"] is not None else "-",
            r["grade"] or "-", r["rank"] or "-",
        ])
    if len(table_data) == 1:
        table_data.append(["No records yet", "", "", "", "", "", "", "", "", ""])

    table = Table(table_data, repeatRows=1)
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.teal),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTSIZE", (0, 0), (-1, -1), 8),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.grey),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.whitesmoke, colors.white]),
    ]))
    story.append(table)
    doc.build(story)
    return path


def expense_report_csv(child_id: int, path: Optional[Path] = None) -> Path:
    child = ChildRepository().get(child_id)
    if child is None:
        raise ValueError(f"No child with id {child_id}")
    expenses = ExpenseRepository().list_for_child(child_id)

    path = path or reports_dir() / _timestamped_name(f"expenses_{child.full_name}", "csv")
    with open(path, "w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh)
        writer.writerow(["Date", "Category", "Academic Year", "Amount", "Description"])
        for e in expenses:
            writer.writerow([e.expense_date, e.category_name, e.year_label, e.amount, e.description])
    return path


def expense_report_excel(child_id: int, path: Optional[Path] = None) -> Path:
    child = ChildRepository().get(child_id)
    if child is None:
        raise ValueError(f"No child with id {child_id}")
    repo = ExpenseRepository()
    expenses = repo.list_for_child(child_id)
    by_category = repo.total_by_category(child_id)

    path = path or reports_dir() / _timestamped_name(f"expenses_{child.full_name}", "xlsx")
    wb = Workbook()

    ws1 = wb.active
    ws1.title = "Expenses"
    ws1.append(["Date", "Category", "Academic Year", "Amount", "Description"])
    for e in expenses:
        ws1.append([e.expense_date, e.category_name, e.year_label, e.amount, e.description])

    ws2 = wb.create_sheet("By Category")
    ws2.append(["Category", "Total"])
    for row in by_category:
        ws2.append([row["category_name"], row["total"]])

    wb.save(str(path))
    return path


def teacher_effectiveness_pdf(teacher_id: int, path: Optional[Path] = None) -> Path:
    _require_pdf()
    from reportlab.lib import colors
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import getSampleStyleSheet
    from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

    teacher = TeacherRepository().get(teacher_id)
    if teacher is None:
        raise ValueError(f"No teacher with id {teacher_id}")
    rows = TeacherRepository().get_teacher_effectiveness(teacher_id)

    path = path or reports_dir() / _timestamped_name(f"teacher_effectiveness_{teacher.name}", "pdf")
    styles = getSampleStyleSheet()
    doc = SimpleDocTemplate(str(path), pagesize=A4)
    story = [
        Paragraph(f"Teacher Effectiveness — {teacher.name}", styles["Title"]),
        Paragraph(f"Subject: {teacher.subject or '-'}  |  School: {teacher.school_name or '-'}", styles["Normal"]),
        Spacer(1, 12),
    ]

    table_data = [["Academic Year", "Average %", "Marks Recorded"]]
    for r in rows:
        avg = r["avg_percentage"]
        table_data.append([r["year_label"], f"{avg:.1f}" if avg is not None else "-", r["mark_count"]])
    if len(table_data) == 1:
        table_data.append(["No records yet", "", ""])

    table = Table(table_data, repeatRows=1)
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.teal),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.grey),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.whitesmoke, colors.white]),
    ]))
    story.append(table)
    doc.build(story)
    return path
