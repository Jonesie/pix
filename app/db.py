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
    _shorten_legacy_ids(conn)


def _shorten_legacy_ids(conn: sqlite3.Connection) -> None:
    # Images created before short ids were introduced still have their
    # original 32-character uuid.hex id. Give them a short one instead.
    # SQLite won't let a UPDATE repoint images.id while image_tags.image_id
    # still references the old value, so insert-under-the-new-id, repoint
    # the tags, then drop the old row — every step keeps the FK satisfied.
    from app.images import ID_LENGTH, generate_id  # local import: avoid a cycle at module load

    legacy = conn.execute(
        f"SELECT * FROM images WHERE length(id) > {ID_LENGTH}"
    ).fetchall()
    for row in legacy:
        old_id = row["id"]
        new_id = generate_id(conn)
        conn.execute(
            """INSERT INTO images
               (id, filename_full, filename_thumb, caption, description,
                created_date, uploaded_at, sequence)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                new_id,
                row["filename_full"],
                row["filename_thumb"],
                row["caption"],
                row["description"],
                row["created_date"],
                row["uploaded_at"],
                row["sequence"],
            ),
        )
        conn.execute("UPDATE image_tags SET image_id = ? WHERE image_id = ?", (new_id, old_id))
        conn.execute("DELETE FROM images WHERE id = ?", (old_id,))


def init_db() -> None:
    with get_conn() as conn:
        conn.executescript(SCHEMA)
        _migrate(conn)
