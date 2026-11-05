# Experimental skeletal animation on the Android GLES backend

**Candidate under test. Do not claim Xiaomi/gameplay support until a complete APK
has been tested on the target device.** The user's existing working menu and
five-frame-model addon (including the confirmed door) must remain intact.

## What is different

The distributed backend requests an ES 3.1 context but uses GLSL 100. Its
original IQM bone upload/selector paths were only enabled for desktop GL 3.
The patch detects ES 3 with vertex texture support, supplies raw byte bone
selectors as float attributes and uploads up to 256 per-actor 4x4 matrices to
an RGBA32F, nearest-filtered texture. Texture unit 17 is used; 0–15 belong to
materials and 16 is reserved for ShadowMap. Actors are uploaded from their own
base in the shared frame buffer, preventing one actor from using another's
matrices. Unweighted vertices and missing support fall back to the old path.

`FShader::Load` intentionally reads the vertex shader from base resource file 0,
so a normal `-file` addon cannot override it. `patch_resource.py` updates only
`shaders_gles/glsl/main.vp` inside the exact original `uzdoom.pk3` and checks
all other ZIP entries. The historical static Abby/car addon is still retained
in the source tree for testing, but must **not** be loaded at runtime because
its zero weights suppress animation. The MD3 addon stays loaded unchanged.

## Reproduce (no game assets are included here)

* Engine source: `manuelsiuro/GZDoom-Android-2026` commit
  `d207c89484b8b8a1a801073c82a5121d1a42a6b5`.
* Apply `engine.patch` from that checkout's root (`git apply engine.patch`).
* Build `arm64-v8a` `uzdoom` with Android NDK r27d and
  its `doom/src/main/jni/Application.mk`, preserving the 16K LOAD alignment and
  `-z nostart-stop-gc`. Copy the resulting libraries into the matching
  `doom/src/main/libs/` folder. Verify ELF dependencies and exports before use.
  The separate test build is restricted to ARM64 (the Xiaomi 11T); do not
  advertise or package the original, unpatched 32-bit engine as fixed.
* `python3 tools/fnaf-gles-skinning/patch_resource.py ORIGINAL_UZDOOM.pk3 OUTPUT.pk3`
  requires SHA-256 `ccd977bf3274d1c0bee8e01c226c356b75a8f7aeae8481a2c86b7d48bce4af47`
  and `zip`; use the generated resource as `doom/src/main/assets/uzdoom.pk3`.
* `python3 tools/fnaf-gles-skinning/gpu_fixture.py` exercises the actual GLSL
  100 shader through headless Mesa/EGL, including actor bases, disabled bones,
  and zero weights.
* `python3 tools/fnaf-gles-skinning/test_models.py GAME.pk3 players --all-clips`
  and repeat with `animatronics` and `other`. These compare sampled original
  IQM vertices and normals, first/middle/last frames of each named clip, with a
  CPU reference. They require NumPy, Mesa EGL/GLES3 and the real game archive.
* `make_engine_fixture.py` constructs an independent small-room WAD/PK3 from
  those game assets for a patched desktop GLES engine. It is not a replacement
  for the original game or an Android test. Generated fixtures stay local.

The mathematical tests sample vertices and frames; they are not proof of
whole-game behavior, phone rendering, lifecycle correctness, or performance.
The target APK also needs its normal APK verification and actual Xiaomi tests.
