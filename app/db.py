import sqlite3
from contextlib import contextmanager
from pathlib import Path

DB_PATH = Path(__import__("os").environ.get("DATA_DIR", "./data")) / "pix.sqlite3"

SCHEMA = """
CREATE TABLE IF NOT EXISTS images (
    id TEXT PRIMARY KEY,
    filename_full TEXT NOT NULL,
    filename_thumb TEXT NOT NULL,
    caption TEXT NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    created_date TEXT NOT NULL,
    uploaded_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS tags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS image_tags (
    image_id TEXT NOT NULL REFERENCES images(id) ON DELETE CASCADE,
    tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (image_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_images_created_date ON images(created_date DESC);
"""


def _connect() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


@contextmanager
def get_conn():
    conn = _connect()
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def _migrate(conn: sqlite3.Connection) -> None:
    cols = {row["name"] for row in conn.execute("PRAGMA table_info(images)")}
    if "sequence" not in cols:
        conn.execute("ALTER TABLE images ADD COLUMN sequence INTEGER")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_images_sequence ON images(sequence)")


def init_db() -> None:
    with get_conn() as conn:
        conn.executescript(SCHEMA)
        _migrate(conn)
