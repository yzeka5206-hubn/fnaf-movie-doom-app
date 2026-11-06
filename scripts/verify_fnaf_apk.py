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
    b"0.9.5-dis-siyah-test",
    b"fnaf_outside_black",
    b"outside_black",
    "DIŞ: SİYAH".encode("utf-8"),
    b"vid_scale_customwidth",
    b"fnaf_cull_stats",
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

# Known originals plus the already phone-confirmed door/frame addon. Checking
# hashes as well as magic rejects silent asset substitutions or LFS mistakes.
ASSET_SHA256 = {
    "assets/game/FNAFMovie.ipk3": "018305370826b7262ec9fb1d300e27b2b95e61d146a32cf633e67d81522bcaa6",
    "assets/game/FiveNightsAtFreddysDoomMOVIE.wad": "553ce0056d8806d87a78832d107e072374a5d78fded09937c3686ee84a74d6d8",
    "assets/game/FiveNightsAtFreddysDoomMOVIE.pk3": "baf29b1f871aad39aee8895b2ead8edf4f48d9d1615bb1a5e79e98191e1d4ea6",
    "assets/game/fnaf-touch-v1.pk3": "5dbdb371a9cab312998509b6eba8139184a5332731ca8f070bebe6ad2f1f5c9e",
    "assets/game/fnaf-model-frames-v1.pk3": "cbba76cb4c8d02b80d74ab326f7b1b7fb878b870bb9d900f180f2ae3d935b806",
}
SHADER_MEMBER = "shaders_gles/glsl/main.vp"
SKELETAL_SHADER_SHA256 = "72c457c6f39614b02cb5bf024b363a3375a7c79ef5385519b536a71c0bec8ab5"
NATIVE_BONE_MARKER = b"FNAF skeletal GLES palette:"
NATIVE_OPT_MARKERS = (b"FNAF_CULL_STATS", b"FNAF_TEXTURE_STATS", b"FNAF_BONE_STATS",
                      b"FNAF_OUTSIDE_STATS")
NATIVE_LIBRARIES = (
    "lib/arm64-v8a/libuzdoom.so",
)
NATIVE_SHA256 = {
    "lib/arm64-v8a/libuzdoom.so": "5e9451e327e0f6d2faf063f14aefbadee9c23a704e4c38b7eaa1d9afacaebdef",
}


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
        if any(marker not in library for marker in NATIVE_OPT_MARKERS):
            raise ValueError(f"Native library is missing optimization instrumentation: {name}")
        if name in NATIVE_SHA256 and hashlib.sha256(library).hexdigest() != NATIVE_SHA256[name]:
            raise ValueError(f"Native skeletal library checksum mismatch: {name}")


def validate_apk(apk: Path) -> dict[str, int]:
    if not apk.is_file():
        raise ValueError(f"APK not found: {apk}")
    sizes: dict[str, int] = {}
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        # This test APK intentionally supports only the target ARM64 phone.
        # Reject stray old ARM32/x86 binaries even if Gradle filters regress.
        native_abis = {name.split('/')[1] for name in names
                       if name.startswith('lib/') and name.endswith('.so')
                       and len(name.split('/')) >= 3}
        if native_abis != {'arm64-v8a'}:
            raise ValueError(f"Unexpected native ABIs in animation test: {sorted(native_abis)}")
        for name, (minimum, magic) in ASSETS.items():
            try:
                entry = archive.getinfo(name)
            except KeyError as exc:
                raise ValueError(f"Required game asset missing: {name}") from exc
            sizes[name] = entry.file_size
            if entry.file_size < minimum:
                raise ValueError(f"Game asset too small (perhaps an LFS pointer): {name}: {entry.file_size}")
            with archive.open(entry) as source:
                prefix = source.read(len(magic))
                if prefix != magic:
                    raise ValueError(f"Game asset has wrong file format: {name}")
                if name in ASSET_SHA256:
                    digest = hashlib.sha256(prefix)
                    for chunk in iter(lambda: source.read(1024 * 1024), b""):
                        digest.update(chunk)
                    if digest.hexdigest() != ASSET_SHA256[name]:
                        raise ValueError(f"Game asset checksum mismatch: {name}")
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
