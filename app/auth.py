import hmac
import os
import time

from fastapi import Request
from itsdangerous import BadSignature, SignatureExpired, URLSafeTimedSerializer

SESSION_COOKIE = "pix_admin"
SESSION_MAX_AGE = 60 * 60 * 24 * 14  # 14 days

_secret = os.environ.get("SESSION_SECRET")
if not _secret:
    raise RuntimeError("SESSION_SECRET environment variable must be set")

_serializer = URLSafeTimedSerializer(_secret, salt="pix-admin-session")


def make_session_token() -> str:
    return _serializer.dumps({"admin": True, "ts": time.time()})


def is_authenticated(request: Request) -> bool:
    token = request.cookies.get(SESSION_COOKIE)
    if not token:
        return False
    try:
        data = _serializer.loads(token, max_age=SESSION_MAX_AGE)
    except (BadSignature, SignatureExpired):
        return False
    return bool(data.get("admin"))


def check_password(password: str) -> bool:
    expected = os.environ.get("ADMIN_PASSWORD")
    if not expected:
        raise RuntimeError("ADMIN_PASSWORD environment variable must be set")
    return hmac.compare_digest(password, expected)
