#!/usr/bin/env python3
"""Offline integrity and integration tests for the 12 original Android themes."""
from pathlib import Path
import hashlib
import re
import struct

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "universe/src/main/assets/universe/themes"
HTML = (ROOT / "universe/src/main/assets/universe/index.html").read_text(encoding="utf-8")
KOTLIN = (ROOT / "app/src/main/java/com/quietmoments/diary/DiaryThemes.kt").read_text(encoding="utf-8")
THEMES = ("moon", "rose", "forest", "sea", "dawn", "night",
          "moon_garden", "meadow", "art_nouveau", "sakura", "neon", "vintage")
NEW_IMAGES = {
    "moon_garden": ("0331edbb3ff47b53d2accb4dd63582ee72db89dfcd5413c1692d99f49b992df7", 98858),
    "meadow": ("4416c21b8c2155c5763c54b1e48a225307946e9b01e430660766e67b44b8c7cd", 128002),
    "art_nouveau": ("5d34ff674c358e0002483951c40a8d2a3f3758983c16bc7866de897f2e3f59d5", 146952),
    "sakura": ("70070634fcebcfc8f55035b0176f1b21c397e6e104ce33c6d583bd2ed68a979c", 53088),
    "neon": ("145fc472a815ff9dbcf325e6f7e5a7cbe548a4c54ec92b62876d3fecb4561e27", 55888),
    "vintage": ("321e56df0c22e0b4e70d76d3337fc13851ee97a46848e8f7110a9c1cf0031a8d", 85802)
}

for theme in THEMES:
    art = ASSETS / f"{theme}.webp"
    assert art.is_file(), f"Missing theme illustration: {art}"
    data = art.read_bytes()
    assert len(data) > 10_000, f"Unexpectedly small image: {theme}"
    assert data[:4] == b"RIFF" and data[8:12] == b"WEBP", f"Invalid WebP: {theme}"
    assert struct.unpack_from("<I", data, 4)[0] + 8 == len(data), f"Truncated WebP: {theme}"
    assert f'"{theme}"' in KOTLIN, f"Native palette missing: {theme}"
    assert f'data-app-theme="{theme}"' in HTML, f"WebView palette missing: {theme}"
    assert f'themes/{theme}.webp' in HTML, f"CSS illustration missing: {theme}"
    if theme in NEW_IMAGES:
        expected_hash, expected_size = NEW_IMAGES[theme]
        assert len(data) == expected_size, f"Incorrect image bytes for {theme}"
        assert hashlib.sha256(data).hexdigest() == expected_hash, f"Image corrupted: {theme}"

native_ids = re.findall(r"DiaryPalette\(\s*\"([a-z_]+)\"", KOTLIN)
assert len(native_ids) == 12 and len(set(native_ids)) == 12, f"Expected 12 unique native themes: {native_ids}"
assert set(native_ids) == set(THEMES), f"Unrecognized theme palettes: {set(native_ids) ^ set(THEMES)}"
print("OK: 12 unique themes, 12 valid WebP images, Kotlin palettes, WebView CSS, new art SHA256")
