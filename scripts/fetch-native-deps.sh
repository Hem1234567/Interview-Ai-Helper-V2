#!/usr/bin/env sh
# Fetch the pinned llama.cpp and whisper.cpp sources into app/src/main/cpp/third_party.
# CMake does the same automatically when the folder is empty; CI runs this first so the sources can be cached.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/cpp/third_party"
LLAMA_TAG="b10108";  LLAMA_SHA="0a50d9909a3478e82679f505bf8595d1eee4b0a8"
WHISPER_TAG="v1.9.5"; WHISPER_SHA="d1be6fde11ac6e0407606b4e42fe72d34add8037"

fetch() { # name url tag sha
  dir="$DEST/$1"
  if [ -d "$dir/.git" ] && [ "$(git -C "$dir" rev-parse HEAD)" = "$4" ]; then echo "$1 already at $3"; return; fi
  rm -rf "$dir"; mkdir -p "$DEST"
  git clone --quiet --depth 1 --branch "$3" "$2" "$dir"
  actual="$(git -C "$dir" rev-parse HEAD)"
  [ "$actual" = "$4" ] || { echo "ERROR: $1 $3 is $actual, expected $4" >&2; exit 1; }
  echo "$1 $3 ready"
}
fetch llama.cpp   https://github.com/ggml-org/llama.cpp   "$LLAMA_TAG"   "$LLAMA_SHA"
fetch whisper.cpp https://github.com/ggml-org/whisper.cpp "$WHISPER_TAG" "$WHISPER_SHA"
