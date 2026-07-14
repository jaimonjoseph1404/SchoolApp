[app]
title = Education Tracker
package.name = educationtracker
package.domain = org.familytools

source.dir = .
source.include_exts = py,png,jpg,kv,atlas,sql
source.exclude_dirs = .venv,.git,.github,.buildozer,bin,tests
source.exclude_patterns = app/data/*,*.pdf,*.xlsx,*.csv

version = 0.1.0
# Staged rollout: start with a minimal, low-risk requirement set to get one
# successful build, then add packages back one at a time. openpyxl is pure
# Python (Excel export always works). Left out for now, each guarded in code
# to degrade gracefully at runtime rather than crash (see chart_service.py /
# report_service.py / backup_service.py / ocr_service.py):
#   - matplotlib: has a p4a recipe but is heavy (freetype/png deps); Analytics
#     falls back to text-only insights without it.
#   - reportlab: its optional C accelerator fails to compile against the p4a
#     Python version (removed CPython Unicode C-API) and has no p4a recipe.
#     PDF export is disabled without it; CSV/Excel export still work.
#   - cryptography: has a p4a recipe but needs a Rust toolchain configured;
#     encrypted backup is disabled without it, plain JSON/ZIP backup isn't.
#   - pytesseract / pypdfium2: pytesseract is pure Python but useless without
#     the Tesseract binary, which has no p4a recipe at all; pypdfium2 ships
#     prebuilt wheels that don't cover Android and would need a from-source
#     build. OCR falls back to manual entry without either (already true even
#     with them installed, since the underlying Tesseract binary is absent).
requirements = python3,kivy==2.3.1,kivymd==1.2.0,plyer==2.1.0,sqlite3,pillow,openpyxl

orientation = portrait
fullscreen = 0

android.permissions = CAMERA,READ_EXTERNAL_STORAGE,WRITE_EXTERNAL_STORAGE,READ_MEDIA_IMAGES
android.api = 34
android.minapi = 24
# Single arch for the first build to keep CI time/risk down (covers the vast
# majority of modern devices); add armeabi-v7a back once this succeeds.
android.archs = arm64-v8a
# Required for non-interactive CI builds — otherwise buildozer blocks
# waiting for an interactive SDK license prompt that never comes.
android.accept_sdk_license = True

[buildozer]
log_level = 2
warn_on_root = 1
