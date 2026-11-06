# Offline updater for the optimized test

This Java-only app reconstructs the **separate** optimized FNAF APK from the
phone-confirmed Animation Test without network access. It selects the patch by
entire source APK size and SHA-256 before creating any output. A mismatch is
rejected; source and reconstructed target hashes are verified before the APK
appears in Downloads.

* Source: run 42140465933, SHA-256
  `6872aa42e59cfd66bfceae35cd1c289382f533dd2b91d57ce0d8bf91e7a256a4`,
  package `com.fnaf.moviedoom.animationtest`.
* Target: run 42215283898, commit `e24a642`, SHA-256
  `f55c97179051b1ae5b36a255baa79d4c33e03c51ae0c15b4b0628f7a68ff9286`,
  package `com.fnaf.moviedoom.optimized`.
* Patch: `repair-optimized.dat` (8,490,655 bytes), SHA-256
  `8dbbeaa83fe3ac25bfb337fa74c85cd69f3554cbe9a78177cdf6b1914670a2ac`.

The refined build-time generator uses bsdiff only to find byte-identical,
forward-only runs; the device's PCH2 reader needs no network or bsdiff runtime.
The patch was replayed with the Java reader against the actual APKs and the
output compared byte-for-byte to the original signed target. On-device helper,
whole-game scenes, performance, and phone data transfer require separate tests.

No application is uninstalled and no old private data is modified. The target
is a different package: **do not uninstall the old game**. The new app has its
own data and does not automatically inherit saves or progress. The updater can
be removed after use. No Android device FPS or rendering claim is made here.
