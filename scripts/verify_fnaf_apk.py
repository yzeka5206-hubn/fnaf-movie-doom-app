#!/usr/bin/env python3
"""Fail CI if the uploaded APK is stale or game assets are LFS pointer files."""
from __future__ import annotations

import hashlib
import sys
import zipfile
from pathlib import Path

BUILD_MARKER = b"FNAF_DIAG_V6_1_2026"
ASSETS = {
    "assets/game/FNAFMovie.ipk3": (10_000, b"PK\x03\x04"),
    "assets/game/FiveNightsAtFreddysDoomMOVIE.wad": (10_000_000, b"PWAD"),
    "assets/game/FiveNightsAtFreddysDoomMOVIE.pk3": (100_000_000, b"PK\x03\x04"),
}


def validate_apk(apk: Path) -> dict[str, int]:
    if not apk.is_file():
        raise ValueError(f"APK not found: {apk}")
    sizes: dict[str, int] = {}
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        for name, (minimum, magic) in ASSETS.items():
            try:
                entry = archive.getinfo(name)
            except KeyError as exc:
                raise ValueError(f"Required game asset missing: {name}") from exc
            sizes[name] = entry.file_size
            if entry.file_size < minimum:
                raise ValueError(f"Game asset too small (perhaps an LFS pointer): {name}: {entry.file_size}")
            with archive.open(entry) as source:
                if source.read(len(magic)) != magic:
                    raise ValueError(f"Game asset has wrong file format: {name}")
        dex_names = [n for n in names if n.startswith("classes") and n.endswith(".dex")]
        if not dex_names or not any(BUILD_MARKER in archive.read(name) for name in dex_names):
            raise ValueError("Diagnostic build marker not found in APK dex files")
    return sizes


def main() -> int:
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <apk-directory>", file=sys.stderr)
        return 2
    apks = sorted(Path(sys.argv[1]).glob("*.apk"))
    if len(apks) != 1:
        print(f"Expected exactly one debug APK; found {len(apks)}", file=sys.stderr)
        return 1
    try:
        apk = apks[0]
        sizes = validate_apk(apk)
        digest = hashlib.sha256()
        with apk.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
        checksum = apk.with_name(apk.name + ".sha256")
        checksum.write_text(f"{digest.hexdigest()}  {apk.name}\n", encoding="utf-8")
        print(f"Verified {apk.name}: diagnostic build marker and all three real game assets present")
        for name, size in sizes.items():
            print(f"  {name}: {size} bytes")
        print(f"SHA-256: {digest.hexdigest()}")
    except (ValueError, OSError, zipfile.BadZipFile) as exc:
        print(f"APK verification failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
