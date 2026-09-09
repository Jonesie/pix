import os
import uuid
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, Request, UploadFile, Form, File, HTTPException, Depends
from fastapi.responses import JSONResponse, FileResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles

from app import auth, images
from app.db import init_db
from app.thumbnails import make_thumbnail, make_video_thumbnail, rotate_image as rotate_image_file

DATA_DIR = Path(os.environ.get("DATA_DIR", "./data"))
FULL_DIR = DATA_DIR / "images" / "full"
THUMB_DIR = DATA_DIR / "images" / "thumb"
CONTENT_DIR = DATA_DIR / "content"
FULL_DIR.mkdir(parents=True, exist_ok=True)
THUMB_DIR.mkdir(parents=True, exist_ok=True)
CONTENT_DIR.mkdir(parents=True, exist_ok=True)

ALLOWED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".gif"}
ALLOWED_VIDEO_EXTENSIONS = {".mp4", ".mov", ".webm", ".m4v"}
ALLOWED_EXTENSIONS = ALLOWED_IMAGE_EXTENSIONS | ALLOWED_VIDEO_EXTENSIONS
MAX_IMAGE_UPLOAD_BYTES = 25 * 1024 * 1024  # 25 MB
MAX_VIDEO_UPLOAD_BYTES = 500 * 1024 * 1024  # 500 MB

app = FastAPI(title="Pix")
init_db()


def require_admin(request: Request) -> None:
    if not auth.is_authenticated(request):
        raise HTTPException(status_code=401, detail="Not authenticated")


def parse_sequence(raw: Optional[str]) -> Optional[int]:
    if raw is None or raw.strip() == "":
        return None
    try:
        return int(raw)
    except ValueError:
        raise HTTPException(status_code=400, detail="Sequence must be a whole number")


def read_content_md(sequence: Optional[int]) -> Optional[str]:
    if sequence is None:
        return None
    path = CONTENT_DIR / f"content_{sequence}.md"
    if not path.is_file():
        return None
    return path.read_text(encoding="utf-8")


# ── Public API ──────────────────────────────────────────────────────────


@app.get("/api/images")
def api_list_images(
    q: Optional[str] = None, tag: Optional[str] = None, offset: int = 0, limit: int = 30
):
    limit = max(1, min(limit, 100))
    items, has_more = images.list_images(search=q, tag=tag, offset=offset, limit=limit)
    return {"items": items, "has_more": has_more, "next_offset": offset + len(items)}


@app.get("/api/images/{image_id}")
def api_get_image(image_id: str):
    item = images.get_image(image_id)
    if not item:
        raise HTTPException(status_code=404, detail="Not found")
    item["content_md"] = read_content_md(item["sequence"])
    item["prev_id"], item["next_id"] = images.get_neighbors(image_id)
    return item


@app.get("/api/tags")
def api_list_tags():
    return {"items": images.list_tags()}


@app.get("/images/thumb/{filename}")
def get_thumb(filename: str):
    path = THUMB_DIR / filename
    if not path.is_file():
        raise HTTPException(status_code=404)
    return FileResponse(
        path,
        media_type="image/webp",
        headers={"Cache-Control": "public, max-age=604800, immutable"},
    )


@app.get("/images/full/{filename}")
def get_full(filename: str):
    path = FULL_DIR / filename
    if not path.is_file():
        raise HTTPException(status_code=404)
    return FileResponse(path, headers={"Cache-Control": "public, max-age=604800, immutable"})


# ── Admin auth ──────────────────────────────────────────────────────────


@app.post("/api/admin/login")
def admin_login(password: str = Form(...)):
    if not auth.check_password(password):
        raise HTTPException(status_code=401, detail="Wrong password")
    token = auth.make_session_token()
    resp = JSONResponse({"ok": True})
    resp.set_cookie(
        auth.SESSION_COOKIE,
        token,
        max_age=auth.SESSION_MAX_AGE,
        httponly=True,
        samesite="lax",
        secure=True,
    )
    return resp


@app.post("/api/admin/logout")
def admin_logout():
    resp = JSONResponse({"ok": True})
    resp.delete_cookie(auth.SESSION_COOKIE)
    return resp


@app.get("/api/admin/session")
def admin_session(request: Request):
    return {"authenticated": auth.is_authenticated(request)}


# ── Admin image management ─────────────────────────────────────────────


@app.get("/api/admin/images", dependencies=[Depends(require_admin)])
def admin_list_images(q: Optional[str] = None, tag: Optional[str] = None, offset: int = 0):
    items, has_more = images.list_images(search=q, tag=tag, offset=offset, limit=50)
    return {"items": items, "has_more": has_more, "next_offset": offset + len(items)}


@app.post("/api/admin/images", dependencies=[Depends(require_admin)])
async def admin_upload_image(
    file: UploadFile = File(...),
    caption: str = Form(""),
    description: str = Form(""),
    tags: str = Form(""),
    created_date: Optional[str] = Form(None),
    sequence: Optional[str] = Form(None),
):
    sequence_val = parse_sequence(sequence)
    ext = Path(file.filename or "").suffix.lower()
    if ext not in ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail=f"Unsupported file type: {ext or '(none)'}")

    is_video = ext in ALLOWED_VIDEO_EXTENSIONS
    media_type = "video" if is_video else "image"
    max_bytes = MAX_VIDEO_UPLOAD_BYTES if is_video else MAX_IMAGE_UPLOAD_BYTES

    image_id = uuid.uuid4().hex
    full_name = f"{image_id}{ext}"
    thumb_name = f"{image_id}.webp"
    full_path = FULL_DIR / full_name

    size = 0
    with full_path.open("wb") as out:
        while chunk := await file.read(1024 * 1024):
            size += len(chunk)
            if size > max_bytes:
                out.close()
                full_path.unlink(missing_ok=True)
                raise HTTPException(
                    status_code=413, detail=f"File too large (max {max_bytes // (1024 * 1024)}MB)"
                )
            out.write(chunk)

    try:
        if is_video:
            make_video_thumbnail(full_path, THUMB_DIR / thumb_name)
        else:
            make_thumbnail(full_path, THUMB_DIR / thumb_name)
    except Exception:
        full_path.unlink(missing_ok=True)
        raise HTTPException(status_code=400, detail="Could not process file")

    real_id = images.create_image(
        filename_full=full_name,
        filename_thumb=thumb_name,
        caption=caption,
        description=description,
        tags_raw=tags,
        created_date=created_date,
        sequence=sequence_val,
        media_type=media_type,
    )
    return images.get_image(real_id)


@app.put("/api/admin/images/{image_id}", dependencies=[Depends(require_admin)])
def admin_update_image(
    image_id: str,
    caption: str = Form(""),
    description: str = Form(""),
    tags: str = Form(""),
    created_date: Optional[str] = Form(None),
    sequence: Optional[str] = Form(None),
):
    updated = images.update_image(
        image_id,
        caption=caption,
        description=description,
        tags_raw=tags,
        created_date=created_date,
        sequence=parse_sequence(sequence),
    )
    if not updated:
        raise HTTPException(status_code=404, detail="Not found")
    return updated


@app.post("/api/admin/images/{image_id}/rotate", dependencies=[Depends(require_admin)])
def admin_rotate_image(image_id: str, degrees: int = Form(...)):
    item = images.get_image(image_id)
    if not item:
        raise HTTPException(status_code=404, detail="Not found")
    if item["media_type"] != "image":
        raise HTTPException(status_code=400, detail="Rotation is only supported for images")

    old_full_path = FULL_DIR / item["filename_full"]
    old_thumb_path = THUMB_DIR / item["filename_thumb"]
    if not old_full_path.is_file():
        raise HTTPException(status_code=404, detail="Image file missing")

    # Rotate into fresh filenames rather than overwriting in place — the
    # full/thumb responses are served with a week-long immutable cache
    # header, so reusing the old name would leave stale copies on screen.
    ext = old_full_path.suffix
    new_id = uuid.uuid4().hex
    new_full_name = f"{new_id}{ext}"
    new_thumb_name = f"{new_id}.webp"
    new_full_path = FULL_DIR / new_full_name

    try:
        rotate_image_file(old_full_path, new_full_path, degrees)
        make_thumbnail(new_full_path, THUMB_DIR / new_thumb_name)
    except ValueError:
        new_full_path.unlink(missing_ok=True)
        raise HTTPException(status_code=400, detail="Unsupported rotation")
    except Exception:
        new_full_path.unlink(missing_ok=True)
        raise HTTPException(status_code=400, detail="Could not rotate image")

    updated = images.update_image_files(image_id, new_full_name, new_thumb_name)
    old_full_path.unlink(missing_ok=True)
    old_thumb_path.unlink(missing_ok=True)
    return updated


@app.delete("/api/admin/images/{image_id}", dependencies=[Depends(require_admin)])
def admin_delete_image(image_id: str):
    deleted = images.delete_image(image_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Not found")
    (FULL_DIR / deleted["filename_full"]).unlink(missing_ok=True)
    (THUMB_DIR / deleted["filename_thumb"]).unlink(missing_ok=True)
    return {"ok": True}


# ── Static frontend ────────────────────────────────────────────────────

STATIC_DIR = Path(__file__).resolve().parent.parent / "static"


@app.get("/admin")
def admin_page():
    return FileResponse(STATIC_DIR / "admin.html")


@app.get("/image")
def image_page():
    return FileResponse(STATIC_DIR / "image.html")


@app.get("/healthz")
def healthz():
    return {"ok": True}


@app.middleware("http")
async def no_cache_app_shell(request: Request, call_next):
    response = await call_next(request)
    if request.url.path not in ("/images/thumb", "/images/full") and not request.url.path.startswith(
        ("/images/thumb/", "/images/full/")
    ):
        response.headers["Cache-Control"] = "no-cache"
    return response


app.mount("/", StaticFiles(directory=STATIC_DIR, html=True), name="static")
