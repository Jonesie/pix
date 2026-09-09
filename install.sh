#!/usr/bin/env bash
# Downloads the latest pix release and sets it up for a Docker self-host.
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/Jonesie/pix/main/install.sh | bash
#   curl -fsSL https://raw.githubusercontent.com/Jonesie/pix/main/install.sh | bash -s -- ~/pix
#
# The optional argument is the install directory (default: ./pix).
set -euo pipefail

REPO="Jonesie/pix"
INSTALL_DIR="${1:-./pix}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required — see https://docs.docker.com/engine/install/" >&2
  exit 1
fi

echo "Looking up the latest pix release..."
release_json=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest")
download_url=$(printf '%s' "$release_json" \
  | grep -o '"browser_download_url": *"[^"]*pix-site\.tar\.gz"' \
  | grep -o 'https://[^"]*')

if [ -z "$download_url" ]; then
  echo "Couldn't find a pix-site.tar.gz asset on the latest release of $REPO." >&2
  exit 1
fi

mkdir -p "$INSTALL_DIR"
echo "Downloading $download_url"
curl -fsSL "$download_url" | tar -xz -C "$INSTALL_DIR" --strip-components=1

cd "$INSTALL_DIR"

if [ ! -f .env ]; then
  cp .env.example .env
  echo
  echo "Created .env from the template. Edit it before starting:"
  echo "  ADMIN_PASSWORD — your /admin login password"
  echo "  SESSION_SECRET — a random secret, e.g.: openssl rand -hex 32"
fi

cat <<EOF

Installed to $INSTALL_DIR.

Next steps:
  cd $INSTALL_DIR
  \${EDITOR:-nano} .env          # set ADMIN_PASSWORD and SESSION_SECRET
  docker compose up -d --build

Then open:
  http://localhost:8000          gallery
  http://localhost:8000/admin    upload / manage

Putting this behind a reverse proxy with a real domain and HTTPS? See nginx/pix.conf.example.
EOF
