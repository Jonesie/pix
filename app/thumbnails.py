from pathlib import Path

from PIL import Image, ImageOps

THUMB_MAX_DIM = 480

# PIL's transpose ops rotate counter-clockwise for ROTATE_90, so the
# clockwise/counter-clockwise degrees a caller asks for map to the
# opposite-sounding constant.
ROTATE_TRANSPOSE = {
    90: Image.Transpose.ROTATE_270,  # visually clockwise 90
    -90: Image.Transpose.ROTATE_90,  # visually counter-clockwise 90
    180: Image.Transpose.ROTATE_180,
}


def make_thumbnail(src_path: Path, dest_path: Path) -> None:
    with Image.open(src_path) as img:
        img = ImageOps.exif_transpose(img)
        img.thumbnail((THUMB_MAX_DIM, THUMB_MAX_DIM), Image.LANCZOS)
        if img.mode not in ("RGB", "L"):
            img = img.convert("RGB")
        dest_path.parent.mkdir(parents=True, exist_ok=True)
        img.save(dest_path, format="WEBP", quality=82, method=6)


def rotate_image(src_path: Path, dest_path: Path, degrees: int) -> None:
    """Rotate src_path by an exact multiple of 90 and write it to dest_path.

    Bakes in any existing EXIF orientation first so the saved pixels match
    what was actually on screen, then applies the requested turn losslessly
    (transpose, not an interpolated rotate) so repeated rotations don't
    degrade the image.
    """
    if degrees not in ROTATE_TRANSPOSE:
        raise ValueError(f"Unsupported rotation: {degrees}")
    with Image.open(src_path) as img:
        fmt = img.format
        oriented = ImageOps.exif_transpose(img)
        rotated = oriented.transpose(ROTATE_TRANSPOSE[degrees])
        rotated.load()
    save_kwargs = {"quality": 92} if fmt == "JPEG" else {}
    dest_path.parent.mkdir(parents=True, exist_ok=True)
    rotated.save(dest_path, format=fmt, **save_kwargs)
