# FNAF 60 FPS target: conservative render candidate

This is an in-place update of the **separate** `com.fnaf.moviedoom.optimized`
test (stable signing certificate unchanged). The 60 FPS setting is a *maximum*,
not proof that the Xiaomi 11T reaches it. No game WAD/PK3, simulation, sound,
actor ticks, saved games, controls, or original menus are intentionally changed.

Changes on top of the skeletal, safe GLES and reversible outside-black patches:

* Both SDL launch paths pass `vid_maxfps 60`, `cl_capfps 0` (so an old archived
  35-tic cap doesn't silently limit interpolation), and `vid_fps 1`. UZDoom's
  existing FPS+frame-time counter is a small noninteractive text overlay in the
  top-right. It counts rendered engine frames, not independently measured
  SurfaceFlinger display presentations. In-game options can toggle it for the
  current session; launch enables it again.
* Android API 30+ receives the system's `Surface.setFrameRate(60, DEFAULT)`
  hint once per created SDL surface, with a guarded fallback. The display/system
  may ignore it or choose 60/120 Hz. This is **not** a fixed 60 FPS guarantee,
  does not force a non-seamless mode switch, and does not integrate Swappy.
* A new conservative OBJ/MD3 model bound is formed from all actual vertices
  (all frames and surfaces for MD3). A main-view draw is rejected only if the
  complete bound is outside a common screen plane; camera, portal, mirrors,
  HUD, orthographic views, invalid bounds and unsupported extrapolation fail
  open. Actor ticks, physics, sound and animation frame calculations proceed.
  `fnaf_static_cull 1` enables it, `fnaf_static_cull_stats` logs counters.
* Low graphics + 480p caps eligible visual textures at **768 px** instead of
  1024. Low + 720p retains 1024; other graphics retain their previous sizes.
  The existing alpha-aware resize excludes fonts, menus, indexed assets and
  camera canvases. This trades a little texture sharpness for lower transfer
  and memory pressure; it is not a guaranteed frame-rate improvement.
* `DIŞ: NORMAL/SİYAH` for NIGHT1–NIGHT5 and all earlier controls, menu, GPU
  skeletal animation, IQM culling, data and signing identity remain intact.

## Tests and limitations

`host_test.cpp` checks 30,000 randomized model/clip transforms including weighted
interpolation, tangencies, invalid matrices and 768 px texture dimensions.
`test_real_models.py` accepts the original game PK3 and frame addon and compares
C++ rejection against independent Python/NumPy clip-plane tests for all OBJ and
MD3 vertices; this is not a renderer. Synthetic desktop GLES fixtures use real
OBJ/MD3 geometry with diagnostic skins, comparing on/off scenes, reappearance,
model ticks, mirror and live camera pixels; they do not run the full game. Java
host tests check 720 profiles, refresh-hint API gating, old-ROM fallback, and
launch/lifecycle contracts. CI rebuilds Android ARM64 native code, checks 16 KiB
alignment/ABI/assets/stable certificate, and builds the APK. Physical Xiaomi
11T frame times, thermal behavior, rendering and gameplay still need testing.

Prior profiling found BSP, portal/frustum culling, IQM draw rejection, texture
resizing and bone-palette reuse already present. On the current low preset
`gl_texture_filter=0` intentionally keeps the original menu's value and uses no
mipmaps; mipmaps might improve bandwidth but introduce allocation/upload costs
and need a device A/B test. Likewise indiscriminate pre-caching, multithreading,
shader changes, ASTC conversion and Swappy were not silently enabled: each has
memory, visual, compatibility or lifecycle tradeoffs not validated here.

References: https://developer.android.com/games/sdk/frame-pacing ,
https://developer.android.com/media/optimize/performance/frame-rate ,
https://developer.android.com/topic/performance/issues/slow-session ,
https://community.arm.com/arm-community-blogs/b/mobile-graphics-and-gaming-blog/posts/accelerating-mali-gpu-analysis-using-arm-mobile-studio .
