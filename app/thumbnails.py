from pathlib import Path

from PIL import Image, ImageOps

THUMB_MAX_DIM = 480


def make_thumbnail(src_path: Path, dest_path: Path) -> None:
    with Image.open(src_path) as img:
        img = ImageOps.exif_transpose(img)
        img.thumbnail((THUMB_MAX_DIM, THUMB_MAX_DIM), Image.LANCZOS)
        if img.mode not in ("RGB", "L"):
            img = img.convert("RGB")
        dest_path.parent.mkdir(parents=True, exist_ok=True)
        img.save(dest_path, format="WEBP", quality=82, method=6)
