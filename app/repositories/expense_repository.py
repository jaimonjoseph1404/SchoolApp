"""Educational Expense Management module (PRD section 9)."""
from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from typing import List, Optional

from app.core.database import get_db


@dataclass
class Expense:
    id: Optional[int]
    child_id: int
    category_id: int
    category_name: str
    amount: float
    expense_date: str
    description: str
    receipt_path: str
    academic_year_id: Optional[int] = None
    year_label: str = ""


class ExpenseRepository:
    def __init__(self, conn: Optional[sqlite3.Connection] = None):
        self._conn = conn or get_db()

    def list_categories(self):
        return self._conn.execute("SELECT * FROM expense_categories ORDER BY name").fetchall()

    def get_or_create_category(self, name: str) -> int:
        name = name.strip()
        row = self._conn.execute(
            "SELECT id FROM expense_categories WHERE name = ?", (name,)
        ).fetchone()
        if row:
            return row["id"]
        cur = self._conn.execute("INSERT INTO expense_categories (name) VALUES (?)", (name,))
        self._conn.commit()
        return cur.lastrowid

    def list_for_child(self, child_id: int) -> List[Expense]:
        rows = self._conn.execute(
            """
            SELECT e.*, ec.name AS category_name, ay.year_label
            FROM expenses e
            JOIN expense_categories ec ON ec.id = e.category_id
            LEFT JOIN academic_years ay ON ay.id = e.academic_year_id
            WHERE e.child_id = ?
            ORDER BY e.expense_date DESC, e.id DESC
            """,
            (child_id,),
        ).fetchall()
        return [
            Expense(
                id=r["id"], child_id=r["child_id"], category_id=r["category_id"],
                category_name=r["category_name"], amount=r["amount"] or 0.0,
                expense_date=r["expense_date"] or "", description=r["description"] or "",
                receipt_path=r["receipt_path"] or "", academic_year_id=r["academic_year_id"],
                year_label=r["year_label"] or "",
            )
            for r in rows
        ]

    def create(
        self, child_id, category_name, amount, expense_date="", description="",
        receipt_path="", academic_year_id=None, class_id=None,
    ) -> int:
        category_id = self.get_or_create_category(category_name)
        cur = self._conn.execute(
            "INSERT INTO expenses (child_id, academic_year_id, class_id, category_id, amount, "
            "expense_date, description, receipt_path) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (child_id, academic_year_id, class_id, category_id, amount, expense_date,
             description, receipt_path),
        )
        self._conn.commit()
        return cur.lastrowid

    def delete(self, expense_id: int) -> None:
        self._conn.execute("DELETE FROM expenses WHERE id = ?", (expense_id,))
        self._conn.commit()

    def total_for_child(self, child_id: int) -> float:
        row = self._conn.execute(
            "SELECT COALESCE(SUM(amount), 0) AS total FROM expenses WHERE child_id = ?", (child_id,)
        ).fetchone()
        return row["total"] or 0.0

    def total_by_category(self, child_id: int):
        return self._conn.execute(
            """
            SELECT ec.name AS category_name, COALESCE(SUM(e.amount), 0) AS total
            FROM expense_categories ec
            LEFT JOIN expenses e ON e.category_id = ec.id AND e.child_id = ?
            GROUP BY ec.id
            HAVING total > 0
            ORDER BY total DESC
            """,
            (child_id,),
        ).fetchall()

    def total_by_year(self, child_id: int):
        return self._conn.execute(
            """
            SELECT COALESCE(ay.year_label, 'Unassigned') AS year_label, SUM(e.amount) AS total
            FROM expenses e
            LEFT JOIN academic_years ay ON ay.id = e.academic_year_id
            WHERE e.child_id = ?
            GROUP BY year_label
            ORDER BY year_label
            """,
            (child_id,),
        ).fetchall()

    def create_fee_receipt(
        self, expense_id, school_name="", receipt_number="", receipt_date="",
        fee_components_json="", amount=None, total_amount=None, image_path="",
    ) -> int:
        cur = self._conn.execute(
            "INSERT INTO fee_receipts (expense_id, school_name, receipt_number, receipt_date, "
            "fee_components_json, amount, total_amount, image_path) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (expense_id, school_name, receipt_number, receipt_date, fee_components_json,
             amount, total_amount, image_path),
        )
        self._conn.commit()
        return cur.lastrowid
