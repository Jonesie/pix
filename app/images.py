import secrets
from datetime import date
from typing import Optional

from app.db import get_conn

# Short, URL-friendly image ids. Excludes 0/1/i/l/o so ids are unambiguous
# to read aloud or type by hand.
ID_ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz"
ID_LENGTH = 6


def generate_id(conn) -> str:
    for _ in range(20):
        candidate = "".join(secrets.choice(ID_ALPHABET) for _ in range(ID_LENGTH))
        exists = conn.execute("SELECT 1 FROM images WHERE id = ?", (candidate,)).fetchone()
        if not exists:
            return candidate
    raise RuntimeError("Could not generate a unique image id")


def _split_tags(raw: str) -> list[str]:
    seen = []
    for part in raw.split(","):
        name = part.strip().lower()
        if name and name not in seen:
            seen.append(name)
    return seen


def _tag_ids(conn, tag_names: list[str]) -> list[int]:
    ids = []
    for name in tag_names:
        conn.execute("INSERT OR IGNORE INTO tags (name) VALUES (?)", (name,))
        row = conn.execute("SELECT id FROM tags WHERE name = ?", (name,)).fetchone()
        ids.append(row["id"])
    return ids


def create_image(
    filename_full: str,
    filename_thumb: str,
    caption: str,
    description: str,
    tags_raw: str,
    created_date: Optional[str],
    sequence: Optional[int] = None,
) -> str:
    created = created_date or date.today().isoformat()
    with get_conn() as conn:
        image_id = generate_id(conn)
        conn.execute(
            """INSERT INTO images
               (id, filename_full, filename_thumb, caption, description, created_date, sequence)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            (image_id, filename_full, filename_thumb, caption, description, created, sequence),
        )
        tag_ids = _tag_ids(conn, _split_tags(tags_raw))
        conn.executemany(
            "INSERT OR IGNORE INTO image_tags (image_id, tag_id) VALUES (?, ?)",
            [(image_id, tid) for tid in tag_ids],
        )
    return image_id


def update_image(
    image_id: str,
    caption: str,
    description: str,
    tags_raw: str,
    created_date: Optional[str],
    sequence: Optional[int] = None,
) -> Optional[dict]:
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM images WHERE id = ?", (image_id,)).fetchone()
        if not row:
            return None
        created = created_date or row["created_date"]
        conn.execute(
            """UPDATE images SET caption = ?, description = ?, created_date = ?, sequence = ?
               WHERE id = ?""",
            (caption, description, created, sequence, image_id),
        )
        conn.execute("DELETE FROM image_tags WHERE image_id = ?", (image_id,))
        tag_ids = _tag_ids(conn, _split_tags(tags_raw))
        conn.executemany(
            "INSERT OR IGNORE INTO image_tags (image_id, tag_id) VALUES (?, ?)",
            [(image_id, tid) for tid in tag_ids],
        )
        row = conn.execute("SELECT * FROM images WHERE id = ?", (image_id,)).fetchone()
        return _to_dict(conn, row)


def _row_tags(conn, image_id: str) -> list[str]:
    rows = conn.execute(
        """SELECT t.name FROM tags t
           JOIN image_tags it ON it.tag_id = t.id
           WHERE it.image_id = ? ORDER BY t.name""",
        (image_id,),
    ).fetchall()
    return [r["name"] for r in rows]


def _to_dict(conn, row) -> dict:
    return {
        "id": row["id"],
        "filename_full": row["filename_full"],
        "filename_thumb": row["filename_thumb"],
        "caption": row["caption"],
        "description": row["description"],
        "sequence": row["sequence"],
        "created_date": row["created_date"],
        "uploaded_at": row["uploaded_at"],
        "tags": _row_tags(conn, row["id"]),
    }


def list_images(
    search: Optional[str] = None,
    tag: Optional[str] = None,
    offset: int = 0,
    limit: int = 30,
) -> tuple[list[dict], bool]:
    with get_conn() as conn:
        where = []
        params: list = []

        if search:
            where.append("(i.caption LIKE ? OR i.description LIKE ?)")
            like = f"%{search}%"
            params += [like, like]

        if tag:
            where.append(
                "i.id IN (SELECT it.image_id FROM image_tags it "
                "JOIN tags t ON t.id = it.tag_id WHERE t.name = ?)"
            )
            params.append(tag.strip().lower())

        where_sql = f"WHERE {' AND '.join(where)}" if where else ""

        rows = conn.execute(
            f"""SELECT i.* FROM images i {where_sql}
                ORDER BY (i.sequence IS NULL) ASC, i.sequence ASC,
                         i.created_date DESC, i.uploaded_at DESC
                LIMIT ? OFFSET ?""",
            (*params, limit + 1, offset),
        ).fetchall()

        has_more = len(rows) > limit
        rows = rows[:limit]
        return [_to_dict(conn, r) for r in rows], has_more


def get_image(image_id: str) -> Optional[dict]:
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM images WHERE id = ?", (image_id,)).fetchone()
        if not row:
            return None
        return _to_dict(conn, row)


def get_neighbors(image_id: str) -> tuple[Optional[str], Optional[str]]:
    with get_conn() as conn:
        rows = conn.execute(
            """SELECT id FROM images
               ORDER BY (sequence IS NULL) ASC, sequence ASC,
                        created_date DESC, uploaded_at DESC"""
        ).fetchall()
        ids = [r["id"] for r in rows]
        try:
            idx = ids.index(image_id)
        except ValueError:
            return None, None
        prev_id = ids[idx - 1] if idx > 0 else None
        next_id = ids[idx + 1] if idx < len(ids) - 1 else None
        return prev_id, next_id


def delete_image(image_id: str) -> Optional[dict]:
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM images WHERE id = ?", (image_id,)).fetchone()
        if not row:
            return None
        data = _to_dict(conn, row)
        conn.execute("DELETE FROM images WHERE id = ?", (image_id,))
        return data


def list_tags() -> list[dict]:
    with get_conn() as conn:
        rows = conn.execute(
            """SELECT t.name, COUNT(it.image_id) AS count
               FROM tags t
               JOIN image_tags it ON it.tag_id = t.id
               GROUP BY t.id
               HAVING count > 0
               ORDER BY count DESC, t.name ASC"""
        ).fetchall()
        return [{"name": r["name"], "count": r["count"]} for r in rows]
