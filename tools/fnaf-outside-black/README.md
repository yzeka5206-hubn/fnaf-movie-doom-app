# Experimental outside-black render mode (NIGHT1–NIGHT5 only)

This is an opt-in render experiment for the exact bundled WAD, not a claim of
better FPS. The original launcher has a persistent `DIŞ: SİYAH / DIŞ: NORMAL`
toggle; changing it applies on the next launch. New installs default to BLACK.
It never modifies the game data, physics, actors, AI, sound, triggers or saves.

The original UDMF maps have a separate outdoor world below y=-2530. Interior
portal IDs 101/103/105/106/107 lead to exterior IDs 100/102/104/108/109.
There are also eight isolated outdoor display/camera cells. In black mode,
the known matching portal renders via the renderer's existing stencil-clipped
black fallback without recursively drawing the outside. Views whose camera is
in the outdoor world or cells clear black instead of traversing/drawing that
scene. The HUD is rendered separately; the user will be unable to see exterior
objects or navigate by sight until changing back to NORMAL. Indoor portals,
reverse portals, other maps and unknown/retargeted lines fail open. This is not
a general scene-culling solution, nor does it stop simulation or all work such
as shadow-map preparation. The original OUTSIDE and MIDNIGHT maps are untouched.

`fnaf_outside_stats` prints cumulative skipped portal/views for diagnostics.
`host_test.cpp` tests positive/negative boundaries. `test_real_maps.py` requires
the original WAD SHA-256 553ce005... and checks all five actual maps and known
portal pairs. Additional graphics and Android device checks remain necessary.
`engine.patch` applies after the skeletal and safe-culling engine patches; the
header alongside it must match the patch. Do not ship the checkout's old native
library: CI must rebuild the ARM64 engine and use the existing stable signing
secrets. Do not uninstall either application or regenerate signing keys.

Research: an independent PC DirectX 11 GTA V frame study observed distance LOD
and selective rendering of reflections (https://www.adriancourreges.com/blog/2015/11/02/gta-v-graphics-study/).
That is not evidence UZDoom uses GTA's engine. Android's frame-pacing guide
explains uneven presentation intervals as a distinct stutter cause
(https://developer.android.com/games/sdk/frame-pacing). FPS, frame times and
thermal/CPU/GPU bottlenecks still require measuring on the Xiaomi.
