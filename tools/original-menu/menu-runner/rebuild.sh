#!/usr/bin/env bash
# Rebuild JNI dispatcher against the app's converted launcher; NDK r27d/API 33.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
[ -x "$BIN/aarch64-linux-android33-clang" ] || { echo 'Set ANDROID_NDK_HOME to Android NDK r27d.' >&2; exit 1; }
for abi in arm64-v8a armeabi-v7a; do
  case "$abi" in
    arm64-v8a) CC="$BIN/aarch64-linux-android33-clang" ;;
    armeabi-v7a) CC="$BIN/armv7a-linux-androideabi33-clang" ;;
  esac
  LIBS="$ROOT/doom/src/main/libs/$abi"
  "$CC" -std=c11 -D_POSIX_C_SOURCE=200809L -fPIC -O2 -fvisibility=hidden -shared \
    -Wl,-z,max-page-size=16384 -Wl,--no-undefined -Wl,-soname,libFnafMenuRunner.so \
    -L"$LIBS" -Wl,-rpath-link,"$LIBS" \
    "$ROOT/tools/original-menu/menu-runner/menu_runner.c" -lChowdren -llog \
    -o "$LIBS/libFnafMenuRunner.so"
  "$BIN/llvm-strip" --strip-unneeded "$LIBS/libFnafMenuRunner.so"
done
