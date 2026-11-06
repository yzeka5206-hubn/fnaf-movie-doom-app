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
* `python3 tools/fnaf-gles-skinning/make_engine_fixture.py GAME.pk3 Mike OUT`
  constructs a tiny room with three live `Walk` actors (world/legs/arm). Other
  accepted groups are the seven remaining player names and Freddy, Bonnie,
  Chica, Foxy or SpringBonnie, plus named jumpscare/prop groups. An optional
  final integer selects a fixed clip frame for comparisons with a CPU
  reference. Without that integer, the generated clip loops, with the same
  `SAF_NOOVERRIDE` behavior used by the game. The tool checks the animation
  exists and that a requested frame is in range. Generate
  nodes with `zdbsp -X -o OUT/fixture-nodes.wad OUT/fixture.wad`, and load the
  resulting WAD and `OUT/fixture.pk3` with an independently installed IWAD.
  For framebuffer comparisons, `--solid-skin` uses three diagnostic colors so
  every mesh is visible. `compare_fixture_frames.py` checks that the three
  silhouettes change; `verify_fixture_pose.py` independently projects every
  IQM vertex at the named frame and compares the resulting silhouettes. The
  reference unpatched GLES renderer correctly fails the animated-pose check.
  Nick and Alan's first-person arms are extremely small in the default view:
  their Walk frames differ by just four screen pixels. For a less ambiguous
  test, generate **both** fixed poses with `--solid-skin --zoom-arm` and pass
  `--zoom-arm` to `verify_fixture_pose.py` or `run_engine_fixtures.py` as well.
  This magnifies/raises only the third test actor; the independent reference
  applies the same diagnostic geometry and does not alter any game asset.
  `--frame-index` tests the classic IQM sprite-frame path used by actual
  jumpscares and props. For Pepsi, `--frame-index --pepsi-md3 ADDON.pk3` uses
  the exact baked MD3 bytes from the shipped frame addon and checks them against
  the original IQM CPU reference. To exercise progress in one running engine,
  generate a `GROUP-live` folder without a fixed frame and use the runner's
  `--live` mode. For another pair (e.g. intro car frames 600 and 700), generate
  those named folders and pass `--frames 600 700`. The runner requires all
  three colors, avoiding a false capture of the cyan progress bar shown during
  startup. Fixtures are diagnostic only, stay local,
  and are neither the game nor an Android test. Their dummy `FrameIndex` seeds
  the reference engine's sprite hash: a `BaseFrame`-only isolated room can
  divide by zero during startup.

## Desktop renderer integration results

A patched desktop GLES 3.2 llvmpipe engine rendered and compared two fixed
poses for all eight selectable player groups (world, first-person legs and
arms), the five main animatronics, five separate jumpscare models, Cupcake,
Freddy Plush, and the actual Pepsi MD3 addon. Each of three colored test actors
changed on screen and matched independently CPU-projected IQM silhouettes
(IoU >= 0.55). The magnified first-person arm test was used for Nick and Alan;
Cupcake and Plush were magnified for visibility. The same groups also moved
between two captures **within one running engine process**. Both curtain IQMs
passed fixed-pose CPU and live-loop checks, and the original intro car/driver
IQMs matched frames 600 and 700 using the sprite `FrameIndex` route.

The original game archives were also loaded in the desktop build with sound
and music disabled. The title and outside intro appeared, the car was visible,
and the log later reached MIDNIGHT. The game logged missing model paths
and material/texture warnings; these were not investigated or declared fixed.
The software renderer became too slow for reliable interactive gameplay tests.
Fixture and desktop intro results do not prove complete gameplay, every
animation, APK behavior or Xiaomi support. A desktop-only compatibility build
was required for the Android fork's resources and ES3 context; those edits
were not shipped in the Android binary.

The mathematical tests sample vertices and frames; they are not proof of
whole-game behavior, phone rendering, lifecycle correctness, or performance.
The target APK also needs its normal APK verification and actual Xiaomi tests.
