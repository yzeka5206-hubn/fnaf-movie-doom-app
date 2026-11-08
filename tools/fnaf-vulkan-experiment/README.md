# Experimental native Vulkan backend for the v16 candidate

**Not a release or device performance claim.** This patch is built after the
existing GLES research patch. The native API is already implemented in the
upstream renderer; we do not use ANGLE, Winlator, emulation, or an external
launcher.

* `API: OPENGL ES` is the default in each original-menu session. The explicit,
  session-only `API: VULKAN` choice requests Vulkan for that game launch and
  never persists to the next menu, so a crash cannot permanently lock it on.
* The runtime reports Vulkan or GLES only after the selected backend rendered a
  frame. If Vulkan throws a caught initialization error and GLES initializes,
  the message says GLES was opened. Native runtime crashes/hangs are **not**
  automatically recovered: return to the launcher and open the menu again.
* An Android 14 public Vulkan hardware report for Xiaomi 11T `21081111RG` has
  `multiDrawIndirect=false` and `maxDrawIndirectCount=1`; the current user's
  exact Android driver has not been measured. Android no longer rejects that
  one optional bit. When it is absent, the shared command wrapper submits each
  indirect draw individually. Other required device bits remain required, and
  the logical-device feature bit stays false if the device reports false. The
  renderer currently uses only direct draws; a source audit fails if new raw
  indirect renderer calls are introduced without review.
* Vulkan frame completion ticks the mobile input lifecycle and reports the
  actual API, without calling the GLES-specific touch drawing code. The Android
  View overlay remains responsible for visible controls. `GAME_OPTION_VULKAN`
  is deliberately not set: it would suppress a valid GLES callback if the
  initialization falls back.
* Both Android backends apply the 480p/720p render selection and the same world
  image size limits. The experimental Vulkan code excludes indexed textures,
  camera canvases and ordinary UI paths, and uses the exact same alpha-aware
  world-image downsampler. Vulkan keeps its own upstream sampler/mipmap logic;
  the GLES-only uniform reuse and low-mipmap sampling optimizations do not turn
  into Vulkan optimizations automatically. CCTV and other shared render-only
  experiments remain as documented separately in `fnaf-fps-research`.
* No flashlight, AI, sounds, actor motion, menus or original effects are
  intentionally disabled. A physical Android test of startup, input, menu,
  cameras, lights, animation, crashes and graphics is **still required**.

Local tests (do not prove Xiaomi compatibility or FPS):

```sh
g++ -std=c++17 -O2 -Wall -Wextra -Werror tools/fnaf-vulkan-experiment/host_test.cpp -o /tmp/fnaf-vk-host
/tmp/fnaf-vk-host
# With libvulkan-dev, glslang-tools, Vulkan validation layer and Mesa lavapipe:
bash tools/fnaf-vulkan-experiment/run_gpu_test.sh
# With the patched upstream source tree:
python3 tools/fnaf-vulkan-experiment/check_raw_indirect.py /path/to/doom/src/main/jni/engine/gzdoom
```

The Vulkan GPU test creates devices with the `multiDrawIndirect` feature both
explicitly disabled and enabled, submits both indexed and non-indexed triangle
commands through the exact same adapter, compares pixels, and checks Vulkan's
validation layer. It also extracts the actual shipped five-plane clip branch
from `uzdoom.pk3` and compares hardware clip distances to the shader fallback
with `shaderClipDistance` explicitly disabled. A 1% boundary-pixel tolerance
is used; the local result differed in 10 of 4,608 pixels with no validation
errors. Passing with llvmpipe is not evidence about the Mali driver or a
full-engine test.
