"""Academic Record Management (PRD section 6).

Hierarchy: Child -> Academic Year -> Class -> Term -> Subject -> Exam -> Marks
"""
from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from typing import List, Optional

from app.core.database import get_db


@dataclass
class MarkRow:
    id: Optional[int]
    exam_id: int
    subject_id: int
    subject_name: str
    marks_obtained: Optional[float]
    max_marks: Optional[float]
    grade: str
    percentage: Optional[float]
    rank: Optional[int]
    remarks: str


class AcademicRepository:
    def __init__(self, conn: Optional[sqlite3.Connection] = None):
        self._conn = conn or get_db()

    # --- Academic Years ---
    def list_academic_years(self, child_id: int):
        return self._conn.execute(
            "SELECT * FROM academic_years WHERE child_id = ? ORDER BY year_label", (child_id,)
        ).fetchall()

    def get_or_create_academic_year(self, child_id: int, year_label: str) -> int:
        year_label = year_label.strip()
        row = self._conn.execute(
            "SELECT id FROM academic_years WHERE child_id = ? AND year_label = ?",
            (child_id, year_label),
        ).fetchone()
        if row:
            return row["id"]
        cur = self._conn.execute(
            "INSERT INTO academic_years (child_id, year_label) VALUES (?, ?)",
            (child_id, year_label),
        )
        self._conn.commit()
        return cur.lastrowid

    # --- Classes ---
    def list_classes(self, academic_year_id: int):
        return self._conn.execute(
            "SELECT * FROM classes WHERE academic_year_id = ? ORDER BY id", (academic_year_id,)
        ).fetchall()

    def get_or_create_class(self, academic_year_id: int, class_name: str, section: str = "") -> int:
        class_name = class_name.strip()
        section = (section or "").strip()
        row = self._conn.execute(
            "SELECT id FROM classes WHERE academic_year_id = ? AND class_name = ? AND IFNULL(section,'') = ?",
            (academic_year_id, class_name, section),
        ).fetchone()
        if row:
            return row["id"]
        cur = self._conn.execute(
            "INSERT INTO classes (academic_year_id, class_name, section) VALUES (?, ?, ?)",
            (academic_year_id, class_name, section),
        )
        self._conn.commit()
        return cur.lastrowid

    # --- Terms ---
    def list_terms(self, class_id: int):
        return self._conn.execute(
            "SELECT * FROM terms WHERE class_id = ? ORDER BY id", (class_id,)
        ).fetchall()

    def get_or_create_term(self, class_id: int, term_name: str) -> int:
        term_name = term_name.strip()
        row = self._conn.execute(
            "SELECT id FROM terms WHERE class_id = ? AND term_name = ?", (class_id, term_name)
        ).fetchone()
        if row:
            return row["id"]
        cur = self._conn.execute(
            "INSERT INTO terms (class_id, term_name) VALUES (?, ?)", (class_id, term_name)
        )
        self._conn.commit()
        return cur.lastrowid

    # --- Subjects (global master list) ---
    def list_subjects(self):
        return self._conn.execute("SELECT * FROM subjects ORDER BY name").fetchall()

    def get_or_create_subject(self, name: str) -> int:
        name = name.strip()
        row = self._conn.execute("SELECT id FROM subjects WHERE name = ?", (name,)).fetchone()
        if row:
            return row["id"]
        cur = self._conn.execute("INSERT INTO subjects (name) VALUES (?)", (name,))
        self._conn.commit()
        return cur.lastrowid

    # --- Exams ---
    def list_exams(self, term_id: int):
        return self._conn.execute(
            "SELECT * FROM exams WHERE term_id = ? ORDER BY id", (term_id,)
        ).fetchall()

    def get_or_create_exam(self, term_id: int, exam_type: str, exam_date: str = "") -> int:
        exam_type = exam_type.strip()
        row = self._conn.execute(
            "SELECT id FROM exams WHERE term_id = ? AND exam_type = ?", (term_id, exam_type)
        ).fetchone()
        if row:
            return row["id"]
        cur = self._conn.execute(
            "INSERT INTO exams (term_id, exam_type, exam_date) VALUES (?, ?, ?)",
            (term_id, exam_type, exam_date or None),
        )
        self._conn.commit()
        return cur.lastrowid

    def delete_exam(self, exam_id: int):
        self._conn.execute("DELETE FROM exams WHERE id = ?", (exam_id,))
        self._conn.commit()

    # --- Marks ---
    def list_marks_for_exam(self, exam_id: int) -> List[MarkRow]:
        rows = self._conn.execute(
            """
            SELECT m.*, s.name AS subject_name
            FROM marks m JOIN subjects s ON s.id = m.subject_id
            WHERE m.exam_id = ? ORDER BY s.name
            """,
            (exam_id,),
        ).fetchall()
        return [
            MarkRow(
                id=r["id"], exam_id=r["exam_id"], subject_id=r["subject_id"],
                subject_name=r["subject_name"], marks_obtained=r["marks_obtained"],
                max_marks=r["max_marks"], grade=r["grade"] or "", percentage=r["percentage"],
                rank=r["rank"], remarks=r["remarks"] or "",
            )
            for r in rows
        ]

    def add_or_update_mark(
        self, exam_id, subject_name, marks_obtained, max_marks,
        grade="", rank=None, remarks="",
    ) -> int:
        subject_id = self.get_or_create_subject(subject_name)
        percentage = None
        try:
            if marks_obtained not in (None, "") and max_marks not in (None, "", 0):
                percentage = round((float(marks_obtained) / float(max_marks)) * 100, 2)
        except (TypeError, ValueError, ZeroDivisionError):
            percentage = None

        existing = self._conn.execute(
            "SELECT id FROM marks WHERE exam_id = ? AND subject_id = ?", (exam_id, subject_id)
        ).fetchone()
        if existing:
            self._conn.execute(
                "UPDATE marks SET marks_obtained=?, max_marks=?, grade=?, percentage=?, rank=?, remarks=? "
                "WHERE id=?",
                (marks_obtained, max_marks, grade, percentage, rank, remarks, existing["id"]),
            )
            mark_id = existing["id"]
        else:
            cur = self._conn.execute(
                "INSERT INTO marks (exam_id, subject_id, marks_obtained, max_marks, grade, percentage, "
                "rank, remarks) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                (exam_id, subject_id, marks_obtained, max_marks, grade, percentage, rank, remarks),
            )
            mark_id = cur.lastrowid
        self._conn.commit()
        return mark_id

    def delete_mark(self, mark_id: int):
        self._conn.execute("DELETE FROM marks WHERE id = ?", (mark_id,))
        self._conn.commit()

    # --- Aggregate view for analytics/reports ---
    def get_marks_history(self, child_id: int):
        """Flat chronological view of every mark recorded for a child."""
        return self._conn.execute(
            """
            SELECT ay.year_label, c.class_name, t.term_name, e.exam_type, e.exam_date,
                   s.name AS subject_name, m.marks_obtained, m.max_marks, m.grade,
                   m.percentage, m.rank, m.remarks
            FROM marks m
            JOIN exams e ON e.id = m.exam_id
            JOIN terms t ON t.id = e.term_id
            JOIN classes c ON c.id = t.class_id
            JOIN academic_years ay ON ay.id = c.academic_year_id
            JOIN subjects s ON s.id = m.subject_id
            WHERE ay.child_id = ?
            ORDER BY ay.year_label, c.id, t.id, e.id
            """,
            (child_id,),
        ).fetchall()

    def has_marks_for_year(self, child_id: int, year_label: str) -> bool:
        row = self._conn.execute(
            """
            SELECT COUNT(*) AS n FROM marks m
            JOIN exams e ON e.id = m.exam_id
            JOIN terms t ON t.id = e.term_id
            JOIN classes c ON c.id = t.class_id
            JOIN academic_years ay ON ay.id = c.academic_year_id
            WHERE ay.child_id = ? AND ay.year_label = ?
            """,
            (child_id, year_label),
        ).fetchone()
        return bool(row and row["n"])
