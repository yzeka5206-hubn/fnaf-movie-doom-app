# FNAF GLES optimization candidate

This is a render-only optimization layered on the already tested skeletal
animation patch in `../fnaf-gles-skinning/engine.patch`. Engine base:
`manuelsiuro/GZDoom-Android-2026` commit
`d207c89484b8b8a1a801073c82a5121d1a42a6b5`.

1. Apply the skeletal-animation patch to the exact base, then apply
   `engine.patch` in this directory. Build `libuzdoom.so` with the same NDK
   settings documented by the skeletal patch. The two standalone headers here
   are copies of the corresponding headers in the patch; keep them identical.
2. Run `g++ -std=c++17 -O2 -Wall -Wextra -Werror host_test.cpp -o /tmp/fnaf-opt-test`
   and `/tmp/fnaf-opt-test`. The property test exercises 30,000 weighted poses,
   including fail-open and unweighted vertices, and validates image resize.
3. With NumPy and the separately obtained original game archive, run
   `OPENBLAS_NUM_THREADS=1 python3 test_real_iqm_cull.py GAME.pk3`. This compiles
   `cull_probe.cpp` and samples all actual animated IQM model vertices and
   first/middle/last poses against several clipping transforms. It is not a
   renderer, gameplay, FPS, camera/portal or Android device test.

## What changes

* Launcher selects true custom-sized 480p or 720p logical buffers (subject to
  the running engine's later settings). `vid_showcurrentscaling` reports the
  actual render dimensions. Touch remains in the existing logical UI space.
* The IQM test uses conservative per-bone boxes and the current skeletal
  palette. Only material setup and draws are suppressed; simulation, animation,
  sound and bone calculations continue. It fails open for unavailable/invalid
  bounds, unsupported matrices, interpolation, HUD, cameras and portal views.
  The original bind-pose bounds are also included for every IQM vertex: if a
  simple material pass or unsupported GPU path draws an unskinned model, its
  visible geometry cannot be rejected just because the animated pose is out.
  `fnaf_iqm_cull` defaults off in the engine; the candidate launcher enables it.
  `fnaf_cull_stats` reports tested/rejected draws.
* The lowest profile caps ordinary RGBA source images in `characters/`,
  `props/`, and `textures/` at 1024 px, using alpha-aware box downsampling.
  Indexed textures, runtime canvases, wipe screens, fonts and menus use the
  existing path. Higher profiles use the original image sizes.
  `fnaf_texture_stats` reports the number resized.
* Duplicate identical bone-palette uploads are skipped; the cache is invalidated
  on GL texture/context reset. `fnaf_bone_stats` reports uploads/reuses.
* `gl_precache 0` avoids eager visual texture upload; sound precaching is not
  changed. This may move a small hitch to first use.

These tests are not proof of GPU correctness on all drivers or faster FPS.
Real scenes, portal/camera behavior, menu touch and performance on the Xiaomi
11T still require separate tests. Keep the prior phone-confirmed build and its
data installed. The prior key cannot sign this new package; the optimized test
uses a separate package and its own stable CI signing key.
