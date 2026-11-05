# GLES vertex-frame animation fallback (partial)

The original Android GLES path did not apply IQM skeletal animation.
`fnaf-model-frames-v1.pk3` is loaded *after* the original game. The separate
static model addon is no longer loaded by the experimental skeletal build. It replaces only these actors' `MODELDEF` mappings with MD3 files
baked from the original IQM frames:

| Actor | Source frames | Approx. GPU vertex buffer |
| --- | ---: | ---: |
| PepsiMan | 26 + repeated loop frame | 2.2 MB |
| SourceDoor | 101 | 2.7 MB |
| SourceLever | 51 | 0.8 MB |
| ZWallFan2 | 101 | 20.8 MB |
| Gate | 201 | 8.7 MB |

The estimate uses 44 bytes per GPU vertex, excludes indices/textures and is
incurred per distinct loaded model, not per actor. It depends on engine version
and is not a phone measurement. No original game files or state mappings are
modified. MD3 positions use inverse MODELDEF scale to preserve precision; meshes,
materials, UVs, triangle indices, original frame order and animations' state
transitions are retained. Pepsi's extra frame 26 repeats frame 0, matching the
original `CYBA A` loop state, which otherwise references past IQM frame 25.

**Not a general skeletal-animation fix.** The main Freddy, Bonnie, Chica, Foxy,
Abby and curtains use named `SetAnimation` sequences and are *not* fixed by
these MD3 substitutions. The opening car uses frame indices but is too large
for this limited conversion. The separate experimental engine patch attempts to
support these directly; it still requires real-game and device verification.

Rebuild, with NumPy and the exact original game archive:

```
python3 tools/fnaf-frame-fallback/build.py /path/to/FiveNightsAtFreddysDoomMOVIE.pk3
FNAF_ORIGINAL_PK3=/path/to/game.pk3 python3 -m unittest scripts.tests.test_fnaf_frame_models -v
```

The SHA-256 is checked before conversion. Every frame is checked against its IQM
bounds; int16 overflow is an error. Update the exact `FRAME_MODELS` length in
`FnafBundledGame.kt` after rebuilding. The unit test compares sample converted
poses and triangles with the original IQM. In a Linux UZDoom 4.14.3 GL2 fixture,
Pepsi Man appeared textured and changed poses across the original frame indices.
The complete original game also parsed the addon and entered NIGHT1. These are
not Xiaomi/Android tests; real device rendering, memory and performance remain
unverified.
