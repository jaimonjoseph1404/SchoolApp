"""Progress Report / Fee Receipt OCR module (PRD section 7).

Uses pytesseract (a wrapper around the system Tesseract binary) for scanned
images, and direct text extraction via pypdfium2 for digital/text-based PDFs
(far more accurate than OCR for that case, matching the PRD's 99%+
digital-PDF accuracy target vs. 95%+ for printed/scanned reports). Falls
back gracefully with a clear status message when the Tesseract binary isn't
installed on the host — manual entry always remains available regardless
(PRD section 7 step 6 requirement).
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from typing import List, Optional

from PIL import Image, ImageOps


@dataclass
class ExtractedMarkRow:
    subject: str
    marks_obtained: Optional[float] = None
    max_marks: Optional[float] = None
    grade: str = ""
    percentage: Optional[float] = None
    rank: Optional[int] = None
    remarks: str = ""


@dataclass
class ExtractedReceipt:
    school_name: str = ""
    receipt_number: str = ""
    receipt_date: str = ""
    amount: Optional[float] = None
    total_amount: Optional[float] = None


def is_ocr_available() -> bool:
    try:
        import pytesseract

        pytesseract.get_tesseract_version()
        return True
    except Exception:
        return False


def ocr_unavailable_message() -> str:
    return (
        "Tesseract OCR engine was not found on this device. Install Tesseract "
        "and ensure it is on PATH, or enter the details manually below."
    )


def _preprocess_image(path: str) -> "Image.Image":
    img = Image.open(path)
    img = ImageOps.exif_transpose(img)
    img = img.convert("L")
    return ImageOps.autocontrast(img)


def extract_text_from_image(path: str) -> str:
    import pytesseract

    return pytesseract.image_to_string(_preprocess_image(path))


def is_digital_pdf(path: str) -> bool:
    """True if the PDF already contains extractable text (not a scanned image)."""
    try:
        import pypdfium2 as pdfium

        pdf = pdfium.PdfDocument(path)
        for page in pdf:
            if page.get_textpage().get_text_range().strip():
                return True
        return False
    except Exception:
        return False


def extract_text_from_pdf(path: str) -> str:
    import pypdfium2 as pdfium

    pdf = pdfium.PdfDocument(path)

    if is_digital_pdf(path):
        return "\n".join(page.get_textpage().get_text_range() for page in pdf)

    import pytesseract

    parts = []
    for page in pdf:
        bitmap = page.render(scale=300 / 72)
        pil_image = ImageOps.autocontrast(bitmap.to_pil().convert("L"))
        parts.append(pytesseract.image_to_string(pil_image))
    return "\n".join(parts)


_MARK_LINE_RE = re.compile(
    r"^(?P<subject>[A-Za-z][A-Za-z .&/-]{2,40}?)\s+"
    r"(?P<obtained>\d{1,3}(?:\.\d+)?)\s*/?\s*"
    r"(?P<max>\d{1,3}(?:\.\d+)?)"
    r"(?:\s+(?P<grade>[A-F][+-]?))?"
    r"(?:\s+(?P<percentage>\d{1,3}(?:\.\d+)?)\s*%)?"
    r"(?:\s+(?:Rank\s*)?(?P<rank>\d{1,3}))?"
    r"\s*(?P<remarks>.*)$"
)


def parse_report_text(text: str) -> List[ExtractedMarkRow]:
    """Best-effort parse of a progress report into subject rows.

    Expects lines roughly shaped like ``"Mathematics 92 100 A 92% 3 Excellent"``.
    Lines that don't match are skipped — the parent reviews/corrects the
    result in the UI (PRD OCR workflow step 6); this is a starting point,
    not a guarantee.
    """
    rows: List[ExtractedMarkRow] = []
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        match = _MARK_LINE_RE.match(line)
        if not match:
            continue
        gd = match.groupdict()
        try:
            obtained = float(gd["obtained"])
            max_marks = float(gd["max"])
        except (TypeError, ValueError):
            continue
        percentage = (
            float(gd["percentage"]) if gd["percentage"]
            else (round(obtained / max_marks * 100, 2) if max_marks else None)
        )
        rows.append(
            ExtractedMarkRow(
                subject=gd["subject"].strip(),
                marks_obtained=obtained,
                max_marks=max_marks,
                grade=(gd["grade"] or "").strip(),
                percentage=percentage,
                rank=int(gd["rank"]) if gd["rank"] else None,
                remarks=(gd["remarks"] or "").strip(),
            )
        )
    return rows


_RECEIPT_PATTERNS = {
    "receipt_number": re.compile(
        r"(?:receipt|invoice)\s*(?:no\.?|number|#)\s*[:\-]?\s*([A-Za-z0-9\-/]+)", re.I
    ),
    "receipt_date": re.compile(r"date\s*[:\-]?\s*([0-3]?\d[/-][01]?\d[/-]\d{2,4})", re.I),
    "total_amount": re.compile(
        r"(?:total|grand total|amount payable)\s*[:\-]?\s*(?:rs\.?|inr|₹)?\s*([\d,]+(?:\.\d{1,2})?)",
        re.I,
    ),
}


def parse_receipt_text(text: str) -> ExtractedReceipt:
    receipt = ExtractedReceipt()
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    if lines:
        receipt.school_name = lines[0][:120]

    match = _RECEIPT_PATTERNS["receipt_number"].search(text)
    if match:
        receipt.receipt_number = match.group(1)

    match = _RECEIPT_PATTERNS["receipt_date"].search(text)
    if match:
        receipt.receipt_date = match.group(1)

    match = _RECEIPT_PATTERNS["total_amount"].search(text)
    if match:
        try:
            receipt.total_amount = float(match.group(1).replace(",", ""))
            receipt.amount = receipt.total_amount
        except ValueError:
            pass

    return receipt
