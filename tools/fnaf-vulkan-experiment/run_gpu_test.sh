#!/usr/bin/env bash
# A Vulkan API/validation regression, not a phone or full-engine test.
set -euo pipefail
D=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
export XDG_RUNTIME_DIR="$T/runtime"; mkdir -p "$XDG_RUNTIME_DIR"; chmod 700 "$XDG_RUNTIME_DIR"
# A caller may choose another Vulkan ICD explicitly; otherwise prefer lavapipe.
if [[ -z "${VK_ICD_FILENAMES:-}" ]]; then
  for icd in /usr/share/vulkan/icd.d/lvp_icd*.json; do
    if [[ -f "$icd" ]]; then export VK_ICD_FILENAMES="$icd"; break; fi
  done
fi
glslangValidator -V "$D/indirect.vert" -o "$T/vertex.spv" >/dev/null
glslangValidator -V "$D/indirect.frag" -o "$T/fragment.spv" >/dev/null
g++ -std=c++17 -O2 -Wall -Wextra -Werror -Wno-missing-field-initializers \
  "$D/vulkan_indirect_test.cpp" -lvulkan -o "$T/vulkan-test"
"$T/vulkan-test" "$T/vertex.spv" "$T/fragment.spv"
# Compare the shipped five-plane shader fallback to native clip distances.
# The feature is explicitly disabled in the fallback VkDevice.
ROOT=$(cd "$D/../.." && pwd)
python3 "$D/build_clip_fixture.py" "$ROOT/doom/src/main/assets/uzdoom.pk3" "$T"
glslangValidator -V "$T/clip.vert" -o "$T/clip-native.vert.spv" >/dev/null
glslangValidator -V "$T/clip.frag" -o "$T/clip-native.frag.spv" >/dev/null
glslangValidator -V -DNO_CLIPDISTANCE_SUPPORT=1 "$T/clip.vert" -o "$T/clip-fallback.vert.spv" >/dev/null
glslangValidator -V -DNO_CLIPDISTANCE_SUPPORT=1 "$T/clip.frag" -o "$T/clip-fallback.frag.spv" >/dev/null
g++ -std=c++17 -O2 -Wall -Wextra -Werror -Wno-missing-field-initializers \
  "$D/vulkan_clip_test.cpp" -lvulkan -o "$T/clip-test"
"$T/clip-test" "$T/clip-native.vert.spv" "$T/clip-native.frag.spv" "$T/clip-fallback.vert.spv" "$T/clip-fallback.frag.spv"
