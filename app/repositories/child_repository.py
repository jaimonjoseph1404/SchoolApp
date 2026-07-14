"""CRUD access for the Child Management module (PRD section 5)."""
from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from typing import List, Optional

from app.core.database import get_db

_COLUMNS = [
    "full_name", "gender", "date_of_birth", "school_name", "admission_number",
    "current_class", "section", "academic_year", "photo_path", "blood_group",
    "parent_notes", "medical_notes", "interests", "career_aspiration",
]


@dataclass
class Child:
    id: Optional[int] = None
    full_name: str = ""
    gender: str = ""
    date_of_birth: str = ""
    school_name: str = ""
    admission_number: str = ""
    current_class: str = ""
    section: str = ""
    academic_year: str = ""
    photo_path: str = ""
    blood_group: str = ""
    parent_notes: str = ""
    medical_notes: str = ""
    interests: str = ""
    career_aspiration: str = ""

    @classmethod
    def from_row(cls, row: sqlite3.Row) -> "Child":
        return cls(**{"id": row["id"], **{col: row[col] or "" for col in _COLUMNS}})


class ChildRepository:
    def __init__(self, conn: Optional[sqlite3.Connection] = None):
        self._conn = conn or get_db()

    def list_all(self) -> List[Child]:
        rows = self._conn.execute(
            "SELECT * FROM children ORDER BY full_name COLLATE NOCASE"
        ).fetchall()
        return [Child.from_row(r) for r in rows]

    def get(self, child_id: int) -> Optional[Child]:
        row = self._conn.execute(
            "SELECT * FROM children WHERE id = ?", (child_id,)
        ).fetchone()
        return Child.from_row(row) if row else None

    def create(self, child: Child) -> int:
        values = [getattr(child, col) for col in _COLUMNS]
        placeholders = ", ".join("?" for _ in _COLUMNS)
        cursor = self._conn.execute(
            f"INSERT INTO children ({', '.join(_COLUMNS)}) VALUES ({placeholders})",
            values,
        )
        self._conn.commit()
        return cursor.lastrowid

    def update(self, child: Child) -> None:
        if child.id is None:
            raise ValueError("Cannot update a child without an id")
        assignments = ", ".join(f"{col} = ?" for col in _COLUMNS)
        values = [getattr(child, col) for col in _COLUMNS] + [child.id]
        self._conn.execute(
            f"UPDATE children SET {assignments}, updated_at = datetime('now') WHERE id = ?",
            values,
        )
        self._conn.commit()

    def delete(self, child_id: int) -> None:
        self._conn.execute("DELETE FROM children WHERE id = ?", (child_id,))
        self._conn.commit()

    def search(self, query: str) -> List[Child]:
        like = f"%{query}%"
        rows = self._conn.execute(
            "SELECT * FROM children WHERE full_name LIKE ? OR school_name LIKE ? "
            "ORDER BY full_name COLLATE NOCASE",
            (like, like),
        ).fetchall()
        return [Child.from_row(r) for r in rows]
