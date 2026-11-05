#!/usr/bin/env bash
# Rebuild the corresponding experimental launcher libraries. Android NDK r27d.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ ! -f "$NDK/build/cmake/android.toolchain.cmake" ]]; then
    echo 'Set ANDROID_NDK_HOME to Android NDK r27d.' >&2; exit 1
fi
OUT="${1:-$ROOT/doom/src/main/libs}"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/original-menu-build.XXXXXXXX")"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT"
tar -xzf "$ROOT/tools/original-menu/launcher-source.tar.gz" -C "$WORK"
SRC="$WORK/launcher-source"
for ABI in arm64-v8a armeabi-v7a; do
    cmake -S "$SRC" -B "$WORK/build-$ABI" \
        -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-33 -DANDROID_STL=c++_static \
        -DANDROID_APP_LIBS="$ROOT/doom/src/main/libs" \
        -DSDL2_INCLUDE_DIR="$SRC/third_party/sdl2-include" \
        -DOPENALSOFT_INCLUDE_DIR="$SRC/base/include/desktop/AL" \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_STANDARD=11 -DCMAKE_POLICY_VERSION_MINIMUM=3.5
    cmake --build "$WORK/build-$ABI" -j2
    mkdir -p "$OUT/$ABI"
    "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded \
        -o "$OUT/$ABI/libChowdren.so" "$WORK/build-$ABI/libChowdren.so"
done
printf 'Launcher native libraries rebuilt under %s\n' "$OUT"
