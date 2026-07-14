PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS schools (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT NOT NULL,
    address         TEXT,
    phone           TEXT,
    email           TEXT,
    notes           TEXT
);

CREATE TABLE IF NOT EXISTS children (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    full_name           TEXT NOT NULL,
    gender              TEXT,
    date_of_birth       TEXT,
    school_id           INTEGER REFERENCES schools(id) ON DELETE SET NULL,
    school_name         TEXT,
    admission_number    TEXT,
    current_class       TEXT,
    section             TEXT,
    academic_year       TEXT,
    photo_path          TEXT,
    blood_group         TEXT,
    parent_notes        TEXT,
    medical_notes       TEXT,
    interests           TEXT,
    career_aspiration   TEXT,
    created_at          TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at          TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS academic_years (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    child_id    INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    year_label  TEXT NOT NULL,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(child_id, year_label)
);

CREATE TABLE IF NOT EXISTS classes (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    academic_year_id    INTEGER NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
    class_name          TEXT NOT NULL,
    section             TEXT
);

CREATE TABLE IF NOT EXISTS terms (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    class_id    INTEGER NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    term_name   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS subjects (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    name    TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS exams (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    term_id     INTEGER NOT NULL REFERENCES terms(id) ON DELETE CASCADE,
    exam_type   TEXT NOT NULL,
    exam_date   TEXT
);

CREATE TABLE IF NOT EXISTS marks (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    exam_id         INTEGER NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    subject_id      INTEGER NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    marks_obtained  REAL,
    max_marks       REAL,
    grade           TEXT,
    percentage      REAL,
    rank            INTEGER,
    remarks         TEXT
);

CREATE TABLE IF NOT EXISTS teachers (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT NOT NULL,
    subject         TEXT,
    qualification   TEXT,
    experience      TEXT,
    phone           TEXT,
    email           TEXT,
    school_id       INTEGER REFERENCES schools(id) ON DELETE SET NULL,
    school_name     TEXT,
    notes           TEXT
);

CREATE TABLE IF NOT EXISTS teacher_assignments (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    child_id            INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    academic_year_id    INTEGER NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
    class_id            INTEGER NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    subject_id          INTEGER NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    teacher_id          INTEGER NOT NULL REFERENCES teachers(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS expense_categories (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    name    TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS expenses (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    child_id            INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    academic_year_id    INTEGER REFERENCES academic_years(id) ON DELETE SET NULL,
    class_id            INTEGER REFERENCES classes(id) ON DELETE SET NULL,
    category_id         INTEGER NOT NULL REFERENCES expense_categories(id),
    amount              REAL NOT NULL,
    expense_date        TEXT,
    description         TEXT,
    receipt_path        TEXT,
    created_at          TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS fee_receipts (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    expense_id          INTEGER REFERENCES expenses(id) ON DELETE CASCADE,
    school_name         TEXT,
    receipt_number      TEXT,
    receipt_date        TEXT,
    fee_components_json TEXT,
    amount              REAL,
    total_amount        REAL,
    image_path          TEXT
);

CREATE TABLE IF NOT EXISTS attachments (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    related_table   TEXT NOT NULL,
    related_id      INTEGER NOT NULL,
    file_path       TEXT NOT NULL,
    file_type       TEXT,
    uploaded_at     TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS ocr_history (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    source_type     TEXT NOT NULL,
    source_path     TEXT,
    extracted_json  TEXT,
    status          TEXT,
    created_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS predictions (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    child_id        INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    prediction_type TEXT NOT NULL,
    target          TEXT,
    predicted_value REAL,
    confidence      REAL,
    created_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS backups (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    file_path   TEXT NOT NULL,
    backup_type TEXT NOT NULL,
    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS settings (
    key     TEXT PRIMARY KEY,
    value   TEXT
);

CREATE INDEX IF NOT EXISTS idx_academic_years_child ON academic_years(child_id);
CREATE INDEX IF NOT EXISTS idx_classes_year ON classes(academic_year_id);
CREATE INDEX IF NOT EXISTS idx_terms_class ON terms(class_id);
CREATE INDEX IF NOT EXISTS idx_exams_term ON exams(term_id);
CREATE INDEX IF NOT EXISTS idx_marks_exam ON marks(exam_id);
CREATE INDEX IF NOT EXISTS idx_marks_subject ON marks(subject_id);
CREATE INDEX IF NOT EXISTS idx_expenses_child ON expenses(child_id);
CREATE INDEX IF NOT EXISTS idx_teacher_assignments_child ON teacher_assignments(child_id);
