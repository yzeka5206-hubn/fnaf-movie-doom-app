# Offline updater for the reversible exterior test

This Java-only helper reconstructs an in-place update of the previously installed
optimized test. It needs no network permission. Before writing any output it
checks the source APK's full size and SHA-256; it also checks the complete
reconstructed APK hash. It does not uninstall or clear any application.

* Source: run 42215283898, versionCode 13, package
  `com.fnaf.moviedoom.optimized`, SHA-256
  `f55c97179051b1ae5b36a255baa79d4c33e03c51ae0c15b4b0628f7a68ff9286`.
* Target: run 42286707847, commit `6ff7b8f`, versionCode 14, **same package and
  signing certificate**, SHA-256
  `becb43039e405b619dc39c8b2d00e5d0ffe991fac1a6bc6e2edba837371b8879`.
* Patch: `repair-optimized.dat` (8,216,901 bytes), SHA-256
  `61c6b445049daa29170c642423dcb36f0994a50095a8608f5c9abb8a72470d91`.

The generator uses bsdiff at build time to locate forward-only matching byte
runs. The phone runs the small PCH2 reader with no internet or bsdiff library.
The actual patch was replayed with the same Java implementation; byte comparison
against the signed target passed. Android 14 CI installs the actual previous
APK, writes a private-data sentinel, runs the helper, and checks that a signed
`install -r` upgrade to version 14 preserves that sentinel.

The helper uses the distinct package `com.fnaf.moviedoom.outsideupdater` so the
older one-time helper need not be removed. Do not uninstall the actual game.
This upgrade preserves the Android app-data directory in the tested scenario;
whole-game behavior, phone storage availability, and Xiaomi frame pacing/FPS
still require device testing. When the player/camera is in the outdoor world,
black mode deliberately makes that world invisible; NORMAL disables it.
