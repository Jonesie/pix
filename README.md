# _pix_

A minimal gallery for my favorite pictures. Scrollable, searchable, filterable
by tag. Full-size images only load when you open one; the index page shows
generated thumbnails. Lives at **https://pix.jonesie.net.nz**.

## Stack

- FastAPI (Python) backend, SQLite for metadata, Pillow for thumbnails.
- Vanilla HTML/CSS/JS frontend — no build step.
- Single container; images and the DB live on a bind-mounted data directory.

## Local dev

```bash
cp .env.example .env        # set ADMIN_PASSWORD and SESSION_SECRET
docker compose up --build
# http://localhost:8000        public gallery
# http://localhost:8000/admin  upload / manage
```

Without Docker:

```bash
python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
ADMIN_PASSWORD=devpass SESSION_SECRET=devsecret DATA_DIR=./data \
  uvicorn app.main:app --reload
```

## Data model

Each image has: caption, description, comma-separated tags, and a created
date (defaults to upload day if not set). Uploading generates a WEBP
thumbnail (max 480px) alongside the original; the original is only served
when an image is opened from the index.

## API

- `GET /api/images?q=&tag=&offset=&limit=` — paginated public listing
- `GET /api/images/{id}` — single image detail
- `GET /api/tags` — tag list with counts
- `POST /api/admin/login` (form: `password`) — sets a signed session cookie
- `POST /api/admin/logout`
- `GET /api/admin/session` — `{authenticated: bool}`
- `POST /api/admin/images` (multipart: `file`, `caption`, `description`,
  `tags`, `created_date`) — admin only
- `DELETE /api/admin/images/{id}` — admin only

## Android app

A native Kotlin/Jetpack Compose client lives in `android/` — browse, search/filter,
log in, upload (camera or gallery picker, images or video), and edit/rotate/delete,
all against the same API above (no separate backend needed). It talks to
`https://pix.jonesie.net.nz` by default (see `ApiConfig.BASE_URL`).

To build and run it:

```bash
cd android
./gradlew assembleDebug   # -> app/build/outputs/apk/debug/app-debug.apk
```

Or open `android/` in Android Studio and run it on an emulator or a phone over USB.
Needs JDK 17+ for the Gradle build (Android Studio bundles its own).

## Production deploy

Deployed as the `pix` service in `~/dev/home_nginx`'s Docker Compose
project, behind nginx at `pix.jonesie.net.nz`. See that repo's
`ARCHITECTURE.md` for the shared-stack layout.
