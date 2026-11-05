# Temporary static models for GLES

Historically, `fnaf-model-static-v1.pk3` was loaded after the game archive. It
is **not loaded** by the experimental skeletal build; its zero weights would
mask the new animations. The retained artifact is only a historical fallback and
regression reference. It contains only four model overrides: Abby's world model, legs and flashlight
arm, plus `CarScene2` used by `MikeNewCarScene` in the OUTSIDE intro script.
The original archive is not modified. Abby uses original frame 0; the opening
car uses original frame 700, near its arrival point. These are deliberately
**static**, not a fix for skeletal animation. In that historical build the car
did not travel through the intro; timing and early camera shots could differ.

Rebuild with `numpy` and the exact original game PK3:

```
python3 tools/fnaf-model-fallback/build.py /path/to/FiveNightsAtFreddysDoomMOVIE.pk3
```

The tool checks the input's SHA-256 and verifies Abby's baked bounding boxes
against the original frame metadata. It preserves names, UVs, textures,
triangles, joints and animation metadata, baking only positions and normals
and setting blend weights to zero. On renderers that do skinning, zero weights
prevent a second transformation; GLES 2 uses the baked vertices directly.

Desktop validation: UZDoom 4.14.3 with GL version forced to 2.1 selected
`GLES_MODE_OGL2` (GLSL 100, no GPU bone animation). A small test room loaded
actual game textures and both original models. Without this addon neither Abby
nor the car was visible; with it both appeared, correctly textured and shaped.
The static models were also visible in the GL 3 renderer. This desktop fixture
is **not** a substitute for testing on the Xiaomi/Android binary, the real
selector or the original intro camera timing.

Automated checks: `FNAF_ORIGINAL_PK3=/path/to/game.pk3 python3 -m unittest
scripts.tests.test_fnaf_static_models -v` validates archive paths, confirms the addon is not loaded,
zero weights, bounds, and that only geometry/normals/weights changed.
