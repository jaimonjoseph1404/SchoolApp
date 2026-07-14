"""Key/value app settings, including security (PIN lock) preferences (PRD section 15)."""
from __future__ import annotations

import hashlib
import os
import sqlite3
from typing import Optional

from app.core.database import get_db

PIN_ENABLED_KEY = "pin_lock_enabled"
PIN_HASH_KEY = "pin_hash"
PIN_SALT_KEY = "pin_salt"
THEME_STYLE_KEY = "theme_style"

_PBKDF2_ITERATIONS = 200_000


class SettingsRepository:
    def __init__(self, conn: Optional[sqlite3.Connection] = None):
        self._conn = conn or get_db()

    def get(self, key: str, default: str = "") -> str:
        row = self._conn.execute("SELECT value FROM settings WHERE key = ?", (key,)).fetchone()
        return row["value"] if row and row["value"] is not None else default

    def set(self, key: str, value: str) -> None:
        self._conn.execute(
            "INSERT INTO settings (key, value) VALUES (?, ?) "
            "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            (key, value),
        )
        self._conn.commit()

    # --- Theme ---
    def get_theme_style(self) -> str:
        return self.get(THEME_STYLE_KEY, "Light")

    def set_theme_style(self, style: str) -> None:
        self.set(THEME_STYLE_KEY, style)

    # --- PIN lock ---
    def is_pin_lock_enabled(self) -> bool:
        return self.get(PIN_ENABLED_KEY, "0") == "1"

    def set_pin(self, pin: str) -> None:
        salt = os.urandom(16)
        pin_hash = hashlib.pbkdf2_hmac("sha256", pin.encode("utf-8"), salt, _PBKDF2_ITERATIONS)
        self.set(PIN_SALT_KEY, salt.hex())
        self.set(PIN_HASH_KEY, pin_hash.hex())
        self.set(PIN_ENABLED_KEY, "1")

    def disable_pin(self) -> None:
        self.set(PIN_ENABLED_KEY, "0")

    def verify_pin(self, pin: str) -> bool:
        salt_hex = self.get(PIN_SALT_KEY)
        stored_hash_hex = self.get(PIN_HASH_KEY)
        if not salt_hex or not stored_hash_hex:
            return False
        salt = bytes.fromhex(salt_hex)
        candidate = hashlib.pbkdf2_hmac("sha256", pin.encode("utf-8"), salt, _PBKDF2_ITERATIONS)
        return candidate.hex() == stored_hash_hex
