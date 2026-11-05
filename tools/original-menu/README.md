# Original FNAF launcher port — source

This is the modified Anaconda/Chowdren-generated runtime and generated event
code used to produce `libChowdren.so` in the Android application. It is an
experimental, ahead-of-time C++ conversion of the user's original launcher,
not a Windows executable, emulator or runtime binary translator.

The GPLv3 source, original notices and licenses are included. The upstream
Anaconda/Chowdren project is https://github.com/fnmwolf/Anaconda; SDL headers
are from SDL 2.0.22 (https://github.com/libsdl-org/SDL). The code here is
modified: Build 296 event/asset conversion fixes, GLES2 renderer, GLSL ES
assets, background audio, startup INI defaults and a typed Android bridge.

Game graphics/audio are separately provided by the game owner. The unrelated,
unused proprietary Dohrma TTF payload was removed from the application asset;
`FontEmbed` is a no-op. Bundled legacy Arial.ttf is excluded from this source.

`make_gles_assets.py` converts a generated copy of `Assets.dat` to GLES2 ahead
of time, removes the unused raw font, and rewrites the offset table. The data
packed with the app is the output, not the Windows launcher executable.

To rebuild the native libraries from this archive, see the adjacent
`rebuild-launcher.sh` in the repository. It uses Android NDK r27d, CMake and
the existing SDL2/OpenAL Android shared libraries in `doom/src/main/libs`.
The Android application itself is built separately with Gradle.

## Experimental status and boundaries

* The launcher asset and C++ event conversion contain the original game's
  media and logic; this is **not** the rejected mockup. The desktop GLES2
  path has been smoke-tested; Android lifecycle/audio/JNI/touch/graphics and
  the actual game start still require a physical-device test.
* Only single player is bridged. Multiplayer is unavailable. The Android
  launcher fixes PBR/shaders off and uses the GLES backend; the original
  control is replaced by a notice rather than pretending to enable it.
* The 480p/720p overlay configures the game's internal framebuffer (480p
  default). Original launcher options are kept inside the launcher. The
  separately added YEDEK button opens the app's save/restore screen.
* No game model/animation fixes are claimed in this menu prototype.
* Debug APK has the separate application ID `com.fnaf.moviedoom.menutest` and
  label `FNAF Menü Testi`, so v7 can remain installed; its files and saves are
  separate and are not automatically copied. Each CI run may use a different
  debug signing key. This is a test-only package, not a stable update channel.

Run `python3 tools/original-menu/test-contract.py`,
`python3 scripts/tests/test_menu_touch_host.py`, and
`python3 -m unittest discover -s scripts/tests -p 'test*.py'` for host
checks. `scripts/tests/run_host_controls.sh` additionally tests the v7 controls
and keyboard using the host JVM and minimal Android fakes. If
`glslangValidator` is on PATH, all 37 converted shader pairs are
compiled and linked in the former; Java/JDK and `readelf` are also required.
The script reports its 360 selection combinations separately. None of these
tests implies successful execution on Android.
After building, `python3 tools/original-menu/verify-apk.py path/to/doom-debug.apk`
checks original media hashes, ARM-only ABIs, library alignment and DEX markers.
Use Android build-tools `apksigner verify --verbose --print-certs` separately
for the signature. These checks cannot prove that the APK runs on a phone.
