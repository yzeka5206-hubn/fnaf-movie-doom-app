# FPS research: render-only candidates for v16

This builds on the previously tested v15 source, not on an arbitrary UZDoom
version. `engine.patch` applies after the four existing engine patches in
`tools/fnaf-gles-optimization/build_android_ci.sh`. It is checked both forwards
and backwards by that script. It contains no desktop-only test shims.

* `fnaf_gl_reuse` avoids redundant light-uniform uploads only after comparing
  the exact bytes and vector count **per linked shader program**, computes the
  model normal matrix again only if the 16 source values change, and follows the
  desktop backend's dirty depth-bias policy. Matrix uniforms still upload; a
  reset/new program makes the associated cache start empty. The bone sampler is
  assigned when a supported program links, and the final per-draw bone index is
  set once. It does not lower the light count or skip the flashlight.
* `fnaf_spot_reuse` uses a 64-entry thread-local, direct-mapped cache keyed by
  the bit patterns of the four angles, to reuse the unchanged spotlight
  direction and cone trigonometry. Collisions cause recomputation, not skipped
  lights. Both flashlight spotlights remain intact.
* The main menu's `DOKU: FPS` setting enables mipmaps only for ordinary eligible
  world/character images at the lowest graphics preset; UI/fonts, indexed
  images and camera canvases remain outside this policy. It bounds those images
  to 512 px at 480p or 768 px at 720p; `DOKU: NORMAL` restores the v15
  768/1024 bounds and nearest filter. Additional mip levels cost memory and may
  or may not help a given GPU. GLES2 without full NPOT mipmap support falls back.
* The main menu's **optional and default-off** `KAMERA: TASARRUF` throttles only
  named `TCAMTEX1` through `TCAMTEX7` screen renders to at most five per second
  and no consecutive render frames. The first screen, reassignment and return
  after being unseen are refreshed immediately. At very low main FPS it can be
  as slow as one camera update every two frames. `KAMERA: NORMAL` restores
  existing refresh behavior. This does not throttle actor/AI/sound/game ticks.
* `DIŞ: NORMAL/SİYAH`, true 480p/720p, FPS overlay, culling, input and actual
  original menu remain present. No new fragment early-out or unsafe sampler/
  texture-bind cache is enabled without device profiling and visual testing.

Diagnostics: `fnaf_render_reuse_stats`, `fnaf_spot_reuse_stats`,
`fnaf_mipmap_stats`, `fnaf_camera_saver_stats`, plus the earlier culling, bone,
texture and exterior counters. The spotlight counts are thread-local.
`fnaf_gl_reuse 0` and `fnaf_spot_reuse 0` are A/B test controls; the production
launcher selects 1. User-facing switches are independently reversible.

Host tests: `g++ -std=c++17 -O2 -Wall -Wextra -Werror
  tools/fnaf-fps-research/host_test.cpp -o /tmp/fnaf-host && /tmp/fnaf-host`.
With Mesa EGL/GLES development packages installed, run
`g++ -std=c++17 -O2 -Wall -Wextra -Werror
  tools/fnaf-fps-research/egl_light_cache_test.cpp -o /tmp/fnaf-egl
  -lEGL -lGLESv2 && EGL_PLATFORM=surfaceless LIBGL_ALWAYS_SOFTWARE=1
  /tmp/fnaf-egl`. The optional Kotlin menu scripts need `ANDROID_JAR` (API 36)
  and `KOTLINC` (2.2.x). GitHub CI checks the old culling/exterior/model suites,
  the new policy suite, signatures, packaged assets and ARM64 alignment.

These correctness tests do **not** prove a speed increase or stable 60 FPS on
Xiaomi 11T. Compare the same in-game location, direction, resolution, thermal
state and counter on the real phone, including a longer run and camera screens.
