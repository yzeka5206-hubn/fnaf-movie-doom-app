#!/usr/bin/env python3
"""Fail CI if the uploaded APK is stale or game assets are LFS pointer files."""
from __future__ import annotations

import hashlib
import io
import sys
import zipfile
from pathlib import Path

BUILD_MARKER = b"FNAF_DIAG_V7_2026"
REQUIRED_DEX_MARKERS = (
    BUILD_MARKER,
    b"0.9.2-animation-test",
    b"FnafMenuActivity",
    b"FnafOptionsActivity",
    b"OriginalMenuActivity",
    "Özgün menü hazırlanıyor...".encode("utf-8"),
    b"LatchingSwitch",
    b"forwardSoftEnter",
    b"FNAF_SYSTEM_KEYBOARD_V7",
    b"KLAVYE",
    b"FnafUiStateReader",
    b"FnafTouchPing",
    b"FnafPauseTap",
    b"fnaf-model-frames-v1.pk3",
    b"fnafCommitText",
    b"SAAT",
    b"KOS",
)
ASSETS = {
    "assets/game/FNAFMovie.ipk3": (10_000, b"PK\x03\x04"),
    "assets/game/FiveNightsAtFreddysDoomMOVIE.wad": (10_000_000, b"PWAD"),
    "assets/game/FiveNightsAtFreddysDoomMOVIE.pk3": (100_000_000, b"PK\x03\x04"),
    "assets/game/fnaf-touch-v1.pk3": (500, b"PK\x03\x04"),
    "assets/game/fnaf-model-static-v1.pk3": (1_000_000, b"PK\x03\x04"),
    "assets/game/fnaf-model-frames-v1.pk3": (1_000_000, b"PK\x03\x04"),
}

SHADER_MEMBER = "shaders_gles/glsl/main.vp"
SKELETAL_SHADER_SHA256 = "72c457c6f39614b02cb5bf024b363a3375a7c79ef5385519b536a71c0bec8ab5"
NATIVE_BONE_MARKER = b"FNAF skeletal GLES palette:"
NATIVE_LIBRARIES = (
    "lib/arm64-v8a/libuzdoom.so",
    "lib/armeabi-v7a/libuzdoom.so",
)


def validate_skeletal_resources(archive: zipfile.ZipFile) -> None:
    try:
        resource = archive.read("assets/uzdoom.pk3")
        with zipfile.ZipFile(io.BytesIO(resource)) as base:
            shader = base.read(SHADER_MEMBER)
    except (KeyError, zipfile.BadZipFile) as exc:
        raise ValueError("Missing or invalid skeletal GLES base shader") from exc
    if hashlib.sha256(shader).hexdigest() != SKELETAL_SHADER_SHA256:
        raise ValueError("Skeletal GLES base shader does not match this build")
    for name in NATIVE_LIBRARIES:
        try:
            library = archive.read(name)
        except KeyError as exc:
            raise ValueError(f"Missing skeletal native library: {name}") from exc
        if not library.startswith(b"\x7fELF") or NATIVE_BONE_MARKER not in library:
            raise ValueError(f"Native library does not contain skeletal support: {name}")


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
        validate_skeletal_resources(archive)
        dex_names = [n for n in names if n.startswith("classes") and n.endswith(".dex")]
        dex_files = [archive.read(name) for name in dex_names]
        # The old static Abby/car poses must not be passed as runtime mods.
        if any(b"fnaf-model-static-v1.pk3" in dex for dex in dex_files):
            raise ValueError("Legacy static model override is still loaded by the app")
        for marker in REQUIRED_DEX_MARKERS:
            if not any(marker in dex for dex in dex_files):
                raise ValueError(f"Required build marker not found in APK dex files: {marker!r}")
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
        print(f"Verified {apk.name}: native menu, touch controls, skeletal resources and game assets present")
        for name, size in sizes.items():
            print(f"  {name}: {size} bytes")
        print(f"SHA-256: {digest.hexdigest()}")
    except (ValueError, OSError, zipfile.BadZipFile) as exc:
        print(f"APK verification failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
