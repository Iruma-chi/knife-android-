#!/usr/bin/env bash
set -euo pipefail

# Official TSUKIHIME logo asset from the official English product website.
# The website credits logo design to WINFANWORKS.
URL="https://tsukihime-en.com/assets/img/main/logo.png"
DEST="app/src/main/res/drawable-nodpi/tsukihime_official_logo.png"
TMP="${DEST}.download"

mkdir -p "$(dirname "$DEST")"
curl --fail --location --retry 3 --retry-delay 2 "$URL" --output "$TMP"

python3 - "$TMP" <<'PY'
import struct
import sys

path = sys.argv[1]
with open(path, "rb") as f:
    header = f.read(24)
if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
    raise SystemExit("Downloaded official logo is not a valid PNG.")
width, height = struct.unpack(">II", header[16:24])
if width < 200 or height < 60 or width > 5000 or height > 3000:
    raise SystemExit(f"Unexpected logo dimensions: {width}x{height}")
print(f"Verified official Tsukihime logo: {width}x{height}")
PY

mv "$TMP" "$DEST"

ICON="app/src/main/res/drawable-nodpi/tsukihime_icon_foreground.png"
mkdir -p /tmp/tsukihime-logo-tool
javac -d /tmp/tsukihime-logo-tool scripts/PrepareTsukihimeLogo.java
java -Djava.awt.headless=true -cp /tmp/tsukihime-logo-tool PrepareTsukihimeLogo "$DEST" "$ICON"
echo "Prepared launcher header logo and adaptive app icon foreground."
