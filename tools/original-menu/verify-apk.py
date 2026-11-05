#!/usr/bin/env python3
"""Verify the exact experimental APK's contents; NOT an Android runtime test."""
import hashlib
import shutil
import struct
import subprocess
import sys
import zipfile
from pathlib import Path

if len(sys.argv) != 2:
    raise SystemExit('Usage: verify-apk.py path/to/doom-debug.apk')
APK = Path(sys.argv[1])
ROOT = Path(__file__).resolve().parents[2]
if shutil.which('aapt'):
    badging = subprocess.check_output(['aapt', 'dump', 'badging', str(APK)], text=True)
    assert "package: name='com.fnaf.moviedoom.menutest'" in badging, 'Test must not overwrite v7'
    assert "sdkVersion:'33'" in badging
    print('PACKAGE OK: side-by-side com.fnaf.moviedoom.menutest, API 33+')
assets = {
    'assets/game/FNAFMovie.ipk3': '018305370826b7262ec9fb1d300e27b2b95e61d146a32cf633e67d81522bcaa6',
    'assets/game/FiveNightsAtFreddysDoomMOVIE.pk3': 'baf29b1f871aad39aee8895b2ead8edf4f48d9d1615bb1a5e79e98191e1d4ea6',
    'assets/game/FiveNightsAtFreddysDoomMOVIE.wad': '553ce0056d8806d87a78832d107e072374a5d78fded09937c3686ee84a74d6d8',
    'assets/original-menu/Assets.dat': 'eb7c62aa6f6f05ffc280122f92a4555ed4e6b59e66977c383e0c07202ee36846',
}
with zipfile.ZipFile(APK) as z:
    names = z.namelist()
    for name, expected in assets.items():
        sha = hashlib.sha256()
        with z.open(name) as stream:
            for chunk in iter(lambda: stream.read(1024*1024), b''): sha.update(chunk)
        assert sha.hexdigest() == expected, f'Wrong/missing full game data: {name}'
        print('HASH OK', name, z.getinfo(name).file_size)
    dex = b''.join(z.read(n) for n in names if n.startswith('classes') and n.endswith('.dex'))
    for marker in [b'FNAF_DIAG_V7_2026', b'FNAF_SYSTEM_KEYBOARD_V7', b'LatchingSwitch',
                   b'OriginalMenuActivity', b'FnafLaunchProfile', b'onSingleplayerRequested',
                   b'onLauncherNotice', b'libChowdren.so', b'0.9.0-original-menu-test']:
        assert marker in dex, f'Missing marker {marker!r}'
    abis = {n.split('/')[1] for n in names if n.startswith('lib/') and n.count('/') >= 2}
    assert abis == {'arm64-v8a', 'armeabi-v7a'}, f'Unexpected ABIs: {abis}'
    for abi in sorted(abis):
        for suffix in ['libChowdren.so','libSDL2.so','libopenal.so','libsaffal.so',
                       'libhidapi.so','libuzdoom.so','libtouchcontrols.so','libzmusic_uz.so']:
            name = f'lib/{abi}/{suffix}'
            assert name in names, f'Missing {name}'
        name = f'lib/{abi}/libChowdren.so'
        assert z.read(name) == (ROOT / f'doom/src/main/libs/{abi}/libChowdren.so').read_bytes()
        for name in [n for n in names if n.startswith(f'lib/{abi}/') and n.endswith('.so')]:
            info = z.getinfo(name)
            assert info.compress_type == 0, f'Compressed library: {name}'
            with APK.open('rb') as f:
                f.seek(info.header_offset)
                header = struct.unpack('<IHHHHHIIIHH', f.read(30))
            offset = info.header_offset + 30 + header[-2] + header[-1]
            assert offset % 16384 == 0, f'Not 16KB ZIP aligned: {name}'
    print('PASS ARM ABI set; native libraries and ZIP alignment; launcher and v7 DEX markers')
sha = hashlib.sha256()
with APK.open('rb') as f:
    for chunk in iter(lambda: f.read(1024*1024), b''): sha.update(chunk)
print('APK SHA-256', sha.hexdigest())
