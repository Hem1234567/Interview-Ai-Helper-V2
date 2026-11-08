#!/usr/bin/env sh
# Fails if any Kotlin file other than ui/Theme.kt defines a colour or imports Material.
set -eu
cd "$(dirname "$0")/.."
SRC=app/src/main/java
bad=$(grep -rnE 'Color\(\s*0x|Color\.(White|Black|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|DarkGray|LightGray)\b' "$SRC" --include=*.kt | grep -v '/ui/Theme.kt' || true)
mat=$(grep -rn 'androidx.compose.material' "$SRC" --include=*.kt || true)
if [ -n "$bad$mat" ]; then echo "$bad"; echo "$mat"; echo "Colours must live in Theme.kt and Material must not be used." >&2; exit 1; fi
echo "colour rules OK"
