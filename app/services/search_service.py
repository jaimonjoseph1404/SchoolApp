"""Global Search module (PRD section 14)."""
from __future__ import annotations

from dataclasses import dataclass
from typing import List

from app.core.database import get_db


@dataclass
class SearchResult:
    category: str  # "Child" | "Subject" | "Teacher" | "Academic Year" | "School" | "Expense Category"
    label: str
    detail: str
    ref_id: int = None


def search_all(query: str, conn=None) -> List[SearchResult]:
    query = query.strip()
    if not query:
        return []
    conn = conn or get_db()
    like = f"%{query}%"
    results: List[SearchResult] = []

    for row in conn.execute(
        "SELECT id, full_name, current_class, school_name FROM children WHERE full_name LIKE ?", (like,)
    ):
        results.append(SearchResult(
            "Child", row["full_name"],
            f"{row['current_class'] or ''} · {row['school_name'] or ''}".strip(" ·"),
            row["id"],
        ))

    for row in conn.execute("SELECT id, name FROM subjects WHERE name LIKE ?", (like,)):
        results.append(SearchResult("Subject", row["name"], "", row["id"]))

    for row in conn.execute(
        "SELECT id, name, subject, school_name FROM teachers WHERE name LIKE ?", (like,)
    ):
        results.append(SearchResult(
            "Teacher", row["name"], f"{row['subject'] or ''} · {row['school_name'] or ''}".strip(" ·"), row["id"]
        ))

    for row in conn.execute(
        "SELECT DISTINCT year_label FROM academic_years WHERE year_label LIKE ?", (like,)
    ):
        results.append(SearchResult("Academic Year", row["year_label"], ""))

    for row in conn.execute("SELECT id, name FROM schools WHERE name LIKE ?", (like,)):
        results.append(SearchResult("School", row["name"], "", row["id"]))

    for row in conn.execute(
        "SELECT DISTINCT school_name FROM children WHERE school_name LIKE ?", (like,)
    ):
        if row["school_name"]:
            results.append(SearchResult("School", row["school_name"], ""))

    for row in conn.execute(
        "SELECT id, name FROM expense_categories WHERE name LIKE ?", (like,)
    ):
        results.append(SearchResult("Expense Category", row["name"], "", row["id"]))

    return results
