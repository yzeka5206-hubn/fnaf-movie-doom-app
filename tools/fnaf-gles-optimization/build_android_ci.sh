#!/usr/bin/env bash
# Rebuild the conservative-culling engine from its exact published upstream.
# This runs on GitHub Actions, not on the player's phone.
set -Eeuo pipefail
REPO=$(pwd)
WORK="${RUNNER_TEMP:?}/fnaf-safe-native"
NDK="${ANDROID_SDK_ROOT:?}/ndk/27.3.13750724"
mkdir -p "$WORK/src"
curl -fsSL --retry 3 'https://codeload.github.com/manuelsiuro/GZDoom-Android-2026/tar.gz/d207c89484b8b8a1a801073c82a5121d1a42a6b5' -o "$WORK/source.tar.gz"
tar -xzf "$WORK/source.tar.gz" --strip-components=1 -C "$WORK/src"
(
 cd "$WORK/src"
 git apply --check "$REPO/tools/fnaf-gles-skinning/engine.patch"
 git apply "$REPO/tools/fnaf-gles-skinning/engine.patch"
 git apply --check "$REPO/tools/fnaf-gles-optimization/engine.patch"
 git apply "$REPO/tools/fnaf-gles-optimization/engine.patch"
 git apply --reverse --check "$REPO/tools/fnaf-gles-optimization/engine.patch"
 git apply --check "$REPO/tools/fnaf-outside-black/engine.patch"
 git apply "$REPO/tools/fnaf-outside-black/engine.patch"
 git apply --reverse --check "$REPO/tools/fnaf-outside-black/engine.patch"
 cmp doom/src/main/jni/engine/gzdoom/src/rendering/hwrenderer/scene/fnaf_outside_black.h "$REPO/tools/fnaf-outside-black/fnaf_outside_black.h"
 git apply --check "$REPO/tools/fnaf-fps-60/engine.patch"
 git apply "$REPO/tools/fnaf-fps-60/engine.patch"
 git apply --reverse --check "$REPO/tools/fnaf-fps-60/engine.patch"
 cmp doom/src/main/jni/engine/gzdoom/src/common/models/fnaf_static_cull.h "$REPO/tools/fnaf-fps-60/fnaf_static_cull.h"
 git apply --check "$REPO/tools/fnaf-fps-research/engine.patch"
 git apply "$REPO/tools/fnaf-fps-research/engine.patch"
 git apply --reverse --check "$REPO/tools/fnaf-fps-research/engine.patch"
 cmp doom/src/main/jni/engine/gzdoom/src/common/rendering/gles/fnaf_light_cache.h "$REPO/tools/fnaf-fps-research/fnaf_light_cache.h"
 cmp doom/src/main/jni/engine/gzdoom/src/common/rendering/gles/fnaf_texture_policy.h "$REPO/tools/fnaf-fps-research/fnaf_texture_policy.h"
 cmp doom/src/main/jni/engine/gzdoom/src/r_data/fnaf_camera_scheduler.h "$REPO/tools/fnaf-fps-research/fnaf_camera_scheduler.h"
 cmp doom/src/main/jni/engine/gzdoom/src/rendering/hwrenderer/fnaf_spot_cache.h "$REPO/tools/fnaf-fps-research/fnaf_spot_cache.h"
 git apply --check "$REPO/tools/fnaf-vulkan-experiment/engine.patch"
 git apply "$REPO/tools/fnaf-vulkan-experiment/engine.patch"
 git apply --reverse --check "$REPO/tools/fnaf-vulkan-experiment/engine.patch"
 cmp doom/src/main/jni/engine/gzdoom/libraries/ZVulkan/include/zvulkan/fnaf_vulkan_compat.h "$REPO/tools/fnaf-vulkan-experiment/fnaf_vulkan_compat.h"
 python3 "$REPO/tools/fnaf-vulkan-experiment/check_raw_indirect.py" doom/src/main/jni/engine/gzdoom
)
"$NDK/ndk-build" -C "$WORK/src/doom/src/main" APP_ABI=arm64-v8a -j4 > "$WORK/ndk-build.log" 2>&1 || { tail -150 "$WORK/ndk-build.log"; exit 1; }
SO="$WORK/src/doom/src/main/libs/arm64-v8a/libuzdoom.so"
OLD="$REPO/doom/src/main/libs/arm64-v8a/libuzdoom.so"
test -s "$SO"
tools="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
"$tools/llvm-readelf" -h "$SO" | grep -E 'Machine:.*AArch64'
"$tools/llvm-readelf" -lW "$SO" | awk '$1=="LOAD" {n++; if ($NF!="0x4000") bad=1} END {if(n<1 || bad) exit 1}'
"$tools/llvm-nm" -D --defined-only "$OLD" | awk '$2 ~ /^[TW]$/ {print $3}' | sort -u > "$WORK/old-functions.txt"
"$tools/llvm-nm" -D --defined-only "$SO" | awk '$2 ~ /^[TW]$/ {print $3}' | sort -u > "$WORK/new-functions.txt"
test "$(wc -l < "$WORK/old-functions.txt")" -gt 2000
"$tools/llvm-readelf" -dW "$OLD" | sed -n 's/.*Shared library: \[\([^]]*\)\].*/\1/p' | sort -u > "$WORK/old-needed.txt"
"$tools/llvm-readelf" -dW "$SO" | sed -n 's/.*Shared library: \[\([^]]*\)\].*/\1/p' | sort -u > "$WORK/new-needed.txt"
diff -u "$WORK/old-needed.txt" "$WORK/new-needed.txt"
comm -23 "$WORK/old-functions.txt" "$WORK/new-functions.txt" > "$WORK/missing-functions.txt"
if [ -s "$WORK/missing-functions.txt" ]; then cat "$WORK/missing-functions.txt"; echo 'Native ABI lost exported functions';exit 1;fi
cp "$SO" "$OLD"
SHA=$(sha256sum "$OLD" | cut -d' ' -f1)
python3 - "$SHA" <<'PY'
from pathlib import Path
import sys
p=Path('scripts/verify_fnaf_apk.py');s=p.read_text();old='5e9451e327e0f6d2faf063f14aefbadee9c23a704e4c38b7eaa1d9afacaebdef'
assert s.count(old)==1
p.write_text(s.replace(old,sys.argv[1]))
print('Updated verifier to the just-built ARM64 engine SHA:',sys.argv[1])
PY
printf 'PASS AArch64, 16 KiB alignment, all prior dynamic functions, fresh SHA\n'
