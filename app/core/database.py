"""SQLite connection management and schema initialization."""
from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import Optional

_SCHEMA_PATH = Path(__file__).with_name("schema.sql")

DEFAULT_DB_DIR = Path(__file__).resolve().parent.parent / "data"
DEFAULT_DB_PATH = DEFAULT_DB_DIR / "schoolapp.db"

_SEED_EXPENSE_CATEGORIES = [
    "Tuition Fee", "Admission Fee", "Books", "Uniform", "Bus Fee",
    "Examination Fee", "Activity Fee", "Sports Fee", "Laboratory Fee",
    "Technology Fee", "Coaching Fee", "Miscellaneous",
]


def resolve_db_path() -> Path:
    """Resolve the SQLite database file location.

    Prefers the running Kivy app's ``user_data_dir`` (the correct private
    storage location on Android and Windows). Falls back to a local
    ``app/data`` directory when no app is running, e.g. plain scripts/tests.
    """
    try:
        from kivy.app import App

        running = App.get_running_app()
        if running is not None:
            return Path(running.user_data_dir) / "schoolapp.db"
    except Exception:
        pass
    return DEFAULT_DB_PATH


def get_connection(db_path: Optional[Path] = None) -> sqlite3.Connection:
    path = db_path or resolve_db_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(path))
    conn.execute("PRAGMA foreign_keys = ON")
    conn.row_factory = sqlite3.Row
    return conn


# Columns added to existing tables after their initial release. CREATE TABLE
# IF NOT EXISTS never alters an already-existing table, so new columns must
# be migrated in here as well or they'll silently never appear on an
# upgraded install.
_ADDITIVE_COLUMNS = {
    "teachers": [("school_name", "TEXT")],
}


def init_db(conn: sqlite3.Connection) -> None:
    schema_sql = _SCHEMA_PATH.read_text(encoding="utf-8")
    conn.executescript(schema_sql)
    _apply_additive_migrations(conn)
    _seed_expense_categories(conn)
    conn.commit()


def _apply_additive_migrations(conn: sqlite3.Connection) -> None:
    for table, columns in _ADDITIVE_COLUMNS.items():
        existing = {row["name"] for row in conn.execute(f"PRAGMA table_info({table})")}
        for name, coldef in columns:
            if name not in existing:
                conn.execute(f"ALTER TABLE {table} ADD COLUMN {name} {coldef}")


def _seed_expense_categories(conn: sqlite3.Connection) -> None:
    conn.executemany(
        "INSERT OR IGNORE INTO expense_categories (name) VALUES (?)",
        [(name,) for name in _SEED_EXPENSE_CATEGORIES],
    )


_connection: Optional[sqlite3.Connection] = None


def get_db() -> sqlite3.Connection:
    """Module-level singleton connection shared by repositories."""
    global _connection
    if _connection is None:
        _connection = get_connection()
        init_db(_connection)
    return _connection


def close_db() -> None:
    global _connection
    if _connection is not None:
        _connection.close()
        _connection = None
