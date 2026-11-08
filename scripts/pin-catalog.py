#!/usr/bin/env python3
"""Print the SHA-256 and exact size Hugging Face publishes for every catalog file.

Usage: python3 scripts/pin-catalog.py
Paste the printed values into ModelCatalog.kt (sha256 = "...") to pin a model to one exact file.
Without a pin the app still verifies each download against the hash Hugging Face reports at download time.
"""
import json, re, sys, urllib.request, pathlib

src = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/java/com/interviewcopilot/app/llm/ModelCatalog.kt"
text = src.read_text()
pairs = re.findall(r'repo = "([^"]+)", file = "([^"]+)"', text)
pairs += [("ggerganov/whisper.cpp", f) for f in re.findall(r'"(ggml-[a-z0-9_.\-]+\.bin)"', text)]
ok = True
for repo, name in pairs:
    url = f"https://huggingface.co/api/models/{repo}/tree/main?recursive=1"
    try:
        entries = json.load(urllib.request.urlopen(url, timeout=30))
    except Exception as e:  # network or auth problem
        print(f"!! {repo}: {e}"); ok = False; continue
    hit = next((e for e in entries if e.get("path") == name), None)
    if not hit:
        print(f"!! {repo}/{name}: NOT FOUND"); ok = False; continue
    lfs = hit.get("lfs", {})
    print(f"{name}\n  size   {hit.get('size')}\n  sha256 {lfs.get('oid')}")
sys.exit(0 if ok else 1)
