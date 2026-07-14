"""Teacher Management module (PRD section 10)."""
from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from typing import List, Optional

from app.core.database import get_db

_COLUMNS = [
    "name", "subject", "qualification", "experience", "phone", "email", "school_name", "notes",
]


@dataclass
class Teacher:
    id: Optional[int] = None
    name: str = ""
    subject: str = ""
    qualification: str = ""
    experience: str = ""
    phone: str = ""
    email: str = ""
    school_name: str = ""
    notes: str = ""

    @classmethod
    def from_row(cls, row: sqlite3.Row) -> "Teacher":
        return cls(id=row["id"], **{col: row[col] or "" for col in _COLUMNS})


@dataclass
class Assignment:
    id: int
    child_id: int
    child_name: str
    academic_year_id: int
    year_label: str
    class_id: int
    class_name: str
    subject_id: int
    subject_name: str
    teacher_id: int
    teacher_name: str


class TeacherRepository:
    def __init__(self, conn: Optional[sqlite3.Connection] = None):
        self._conn = conn or get_db()

    def list_all(self) -> List[Teacher]:
        rows = self._conn.execute(
            "SELECT * FROM teachers ORDER BY name COLLATE NOCASE"
        ).fetchall()
        return [Teacher.from_row(r) for r in rows]

    def get(self, teacher_id: int) -> Optional[Teacher]:
        row = self._conn.execute("SELECT * FROM teachers WHERE id = ?", (teacher_id,)).fetchone()
        return Teacher.from_row(row) if row else None

    def create(self, teacher: Teacher) -> int:
        values = [getattr(teacher, col) for col in _COLUMNS]
        placeholders = ", ".join("?" for _ in _COLUMNS)
        cur = self._conn.execute(
            f"INSERT INTO teachers ({', '.join(_COLUMNS)}) VALUES ({placeholders})", values
        )
        self._conn.commit()
        return cur.lastrowid

    def update(self, teacher: Teacher) -> None:
        if teacher.id is None:
            raise ValueError("Cannot update a teacher without an id")
        assignments = ", ".join(f"{col} = ?" for col in _COLUMNS)
        values = [getattr(teacher, col) for col in _COLUMNS] + [teacher.id]
        self._conn.execute(f"UPDATE teachers SET {assignments} WHERE id = ?", values)
        self._conn.commit()

    def delete(self, teacher_id: int) -> None:
        self._conn.execute("DELETE FROM teachers WHERE id = ?", (teacher_id,))
        self._conn.commit()

    # --- Teacher assignments (Child -> Year -> Class -> Subject -> Teacher) ---
    def assign(self, child_id: int, academic_year_id: int, class_id: int, subject_id: int, teacher_id: int) -> int:
        existing = self._conn.execute(
            "SELECT id FROM teacher_assignments WHERE child_id=? AND academic_year_id=? AND class_id=? "
            "AND subject_id=?",
            (child_id, academic_year_id, class_id, subject_id),
        ).fetchone()
        if existing:
            self._conn.execute(
                "UPDATE teacher_assignments SET teacher_id = ? WHERE id = ?", (teacher_id, existing["id"])
            )
            self._conn.commit()
            return existing["id"]
        cur = self._conn.execute(
            "INSERT INTO teacher_assignments (child_id, academic_year_id, class_id, subject_id, teacher_id) "
            "VALUES (?, ?, ?, ?, ?)",
            (child_id, academic_year_id, class_id, subject_id, teacher_id),
        )
        self._conn.commit()
        return cur.lastrowid

    def list_assignments(self) -> List[Assignment]:
        rows = self._conn.execute(
            """
            SELECT ta.id, ta.child_id, ch.full_name AS child_name,
                   ta.academic_year_id, ay.year_label,
                   ta.class_id, c.class_name,
                   ta.subject_id, s.name AS subject_name,
                   ta.teacher_id, t.name AS teacher_name
            FROM teacher_assignments ta
            JOIN children ch ON ch.id = ta.child_id
            JOIN academic_years ay ON ay.id = ta.academic_year_id
            JOIN classes c ON c.id = ta.class_id
            JOIN subjects s ON s.id = ta.subject_id
            JOIN teachers t ON t.id = ta.teacher_id
            ORDER BY ay.year_label DESC, ch.full_name
            """
        ).fetchall()
        return [Assignment(**dict(r)) for r in rows]

    def delete_assignment(self, assignment_id: int) -> None:
        self._conn.execute("DELETE FROM teacher_assignments WHERE id = ?", (assignment_id,))
        self._conn.commit()

    def get_teacher_effectiveness(self, teacher_id: int):
        """Average subject percentage across all children/exams taught by this teacher, per academic year."""
        return self._conn.execute(
            """
            SELECT ay.year_label, AVG(m.percentage) AS avg_percentage, COUNT(*) AS mark_count
            FROM teacher_assignments ta
            JOIN academic_years ay ON ay.id = ta.academic_year_id
            JOIN classes c ON c.id = ta.class_id
            JOIN terms t ON t.class_id = c.id
            JOIN exams e ON e.term_id = t.id
            JOIN marks m ON m.exam_id = e.id AND m.subject_id = ta.subject_id
            WHERE ta.teacher_id = ?
            GROUP BY ay.year_label
            ORDER BY ay.year_label
            """,
            (teacher_id,),
        ).fetchall()
