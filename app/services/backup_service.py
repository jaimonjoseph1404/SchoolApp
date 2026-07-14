"""Backup and Restore module (PRD section 12).

Plain JSON/ZIP export-import plus a password-protected AES-256-GCM encrypted
backup format. Note: this encrypts *backup files*, not the live SQLite
database at rest (that would require SQLCipher, which has no prebuilt wheel
for this Python/Windows/Android combination without a custom native build —
out of scope here). PIN/password app-lock (see settings_repository) covers
access control for the live app.
"""
from __future__ import annotations

import json
import os
import shutil
import zipfile
from datetime import datetime
from pathlib import Path
from typing import Optional

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC

from app.core.database import get_db

# Parents before children, so a restore can insert in this order with
# foreign_keys temporarily disabled.
TABLE_ORDER = [
    "schools", "children", "academic_years", "classes", "terms", "subjects",
    "exams", "marks", "teachers", "teacher_assignments", "expense_categories",
    "expenses", "fee_receipts", "attachments", "ocr_history", "predictions",
    "backups", "settings",
]

ATTACHMENT_FIELDS = {
    "children": ["photo_path"],
    "expenses": ["receipt_path"],
    "fee_receipts": ["image_path"],
}

MAGIC = b"EDU1"
_PBKDF2_ITERATIONS = 200_000


def _app_data_dir() -> Path:
    try:
        from kivy.app import App

        running = App.get_running_app()
        if running is not None:
            return Path(running.user_data_dir)
    except Exception:
        pass
    return Path(__file__).resolve().parent.parent / "data"


def backups_dir() -> Path:
    path = _app_data_dir() / "backups"
    path.mkdir(parents=True, exist_ok=True)
    return path


def attachments_restore_dir() -> Path:
    path = _app_data_dir() / "attachments"
    path.mkdir(parents=True, exist_ok=True)
    return path


def _timestamped_name(prefix: str, ext: str) -> str:
    return f"{prefix}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.{ext}"


def record_backup(path: Path, backup_type: str, conn=None) -> None:
    conn = conn or get_db()
    conn.execute(
        "INSERT INTO backups (file_path, backup_type) VALUES (?, ?)", (str(path), backup_type)
    )
    conn.commit()


def last_backup(conn=None):
    conn = conn or get_db()
    return conn.execute("SELECT * FROM backups ORDER BY created_at DESC LIMIT 1").fetchone()


def export_all(conn=None) -> dict:
    conn = conn or get_db()
    data = {}
    for table in TABLE_ORDER:
        rows = conn.execute(f"SELECT * FROM {table}").fetchall()
        data[table] = [dict(r) for r in rows]
    return data


def restore_from_dict(data: dict, conn=None) -> None:
    conn = conn or get_db()
    conn.execute("PRAGMA foreign_keys = OFF")
    try:
        for table in reversed(TABLE_ORDER):
            conn.execute(f"DELETE FROM {table}")
        for table in TABLE_ORDER:
            for row in data.get(table, []):
                columns = list(row.keys())
                placeholders = ", ".join("?" for _ in columns)
                conn.execute(
                    f"INSERT INTO {table} ({', '.join(columns)}) VALUES ({placeholders})",
                    [row[c] for c in columns],
                )
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.execute("PRAGMA foreign_keys = ON")


# --- Plain JSON ---
def export_json(path: Optional[Path] = None, conn=None) -> Path:
    path = path or backups_dir() / _timestamped_name("backup", "json")
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(export_all(conn), fh, indent=2, default=str)
    record_backup(path, "json", conn=conn)
    return path


def import_json(path: Path, conn=None) -> None:
    with open(path, "r", encoding="utf-8") as fh:
        data = json.load(fh)
    restore_from_dict(data, conn=conn)


# --- ZIP (JSON + attachment files) ---
def export_zip(path: Optional[Path] = None, conn=None) -> Path:
    data = export_all(conn)
    path = path or backups_dir() / _timestamped_name("backup", "zip")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
        for table, fields in ATTACHMENT_FIELDS.items():
            for row in data.get(table, []):
                for field in fields:
                    src = row.get(field)
                    if src and Path(src).exists():
                        arcname = f"files/{Path(src).name}"
                        zf.write(src, arcname)
                        row[field] = arcname
        zf.writestr("data.json", json.dumps(data, indent=2, default=str))
    record_backup(path, "zip", conn=conn)
    return path


def import_zip(path: Path, conn=None) -> None:
    extract_dir = attachments_restore_dir()
    with zipfile.ZipFile(path, "r") as zf:
        data = json.loads(zf.read("data.json").decode("utf-8"))
        for table, fields in ATTACHMENT_FIELDS.items():
            for row in data.get(table, []):
                for field in fields:
                    rel = row.get(field)
                    if rel and str(rel).startswith("files/"):
                        target = extract_dir / Path(rel).name
                        with zf.open(rel) as src, open(target, "wb") as dst:
                            shutil.copyfileobj(src, dst)
                        row[field] = str(target)
    restore_from_dict(data, conn=conn)


# --- AES-256-GCM encrypted backup ---
def _derive_key(password: str, salt: bytes) -> bytes:
    kdf = PBKDF2HMAC(algorithm=hashes.SHA256(), length=32, salt=salt, iterations=_PBKDF2_ITERATIONS)
    return kdf.derive(password.encode("utf-8"))


def export_encrypted(password: str, path: Optional[Path] = None, conn=None) -> Path:
    payload = json.dumps(export_all(conn), default=str).encode("utf-8")
    salt = os.urandom(16)
    nonce = os.urandom(12)
    key = _derive_key(password, salt)
    ciphertext = AESGCM(key).encrypt(nonce, payload, None)

    path = path or backups_dir() / _timestamped_name("backup_encrypted", "bak")
    with open(path, "wb") as fh:
        fh.write(MAGIC + salt + nonce + ciphertext)
    record_backup(path, "encrypted", conn=conn)
    return path


def import_encrypted(password: str, path: Path, conn=None) -> None:
    raw = Path(path).read_bytes()
    if raw[:4] != MAGIC:
        raise ValueError("Not a recognized encrypted backup file")
    salt, nonce, ciphertext = raw[4:20], raw[20:32], raw[32:]
    key = _derive_key(password, salt)
    try:
        payload = AESGCM(key).decrypt(nonce, ciphertext, None)
    except Exception as exc:
        raise ValueError("Incorrect password or corrupted backup file") from exc
    restore_from_dict(json.loads(payload.decode("utf-8")), conn=conn)
