[app]
title = Education Tracker
package.name = educationtracker
package.domain = org.familytools

source.dir = .
source.include_exts = py,png,jpg,kv,atlas,sql
source.exclude_dirs = .venv,.git,.github,.buildozer,bin,tests
source.exclude_patterns = app/data/*,*.pdf,*.xlsx,*.csv

version = 0.1.0
# matplotlib, numpy, reportlab, openpyxl and cryptography all have working
# python-for-android recipes. pytesseract itself is pure Python, but the
# Tesseract OCR *binary* it wraps does not ship as a p4a recipe today — it
# needs a custom recipe (cross-compiled tesseract + leptonica .so) before
# on-device OCR works on Android. Until that recipe exists, the app degrades
# gracefully to manual entry on Android (see app/services/ocr_service.py).
requirements = python3,kivy==2.3.1,kivymd==1.2.0,plyer==2.1.0,sqlite3,pillow,matplotlib,numpy,reportlab,openpyxl,cryptography,pytesseract,pypdfium2

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
