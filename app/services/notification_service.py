"""Notification module (PRD section 13) — derived, in-app notifications.

Only implements what the current schema can honestly support: "exam result
not uploaded" and "backup reminder". Fee-due reminders and academic-year
transition alerts would need due-date fields the PRD's entity list (section
20) doesn't define — that's a schema follow-up, not something to fake here.
"""
from __future__ import annotations

from datetime import datetime, timedelta
from typing import List

from app.core.database import get_db
from app.repositories.academic_repository import AcademicRepository
from app.repositories.child_repository import ChildRepository
from app.services.backup_service import last_backup

BACKUP_REMINDER_DAYS = 30


def get_notifications(conn=None) -> List[str]:
    conn = conn or get_db()
    notifications: List[str] = []

    academic = AcademicRepository()
    for child in ChildRepository(conn).list_all():
        if not child.academic_year:
            continue
        if not academic.has_marks_for_year(child.id, child.academic_year):
            notifications.append(
                f"{child.full_name}: no exam results uploaded yet for {child.academic_year}."
            )

    last = last_backup(conn)
    if last is None:
        notifications.append("You haven't created a backup yet.")
    else:
        created = datetime.strptime(last["created_at"], "%Y-%m-%d %H:%M:%S")
        if datetime.now() - created > timedelta(days=BACKUP_REMINDER_DAYS):
            notifications.append(f"Last backup was on {last['created_at']} — consider creating a new one.")

    return notifications
