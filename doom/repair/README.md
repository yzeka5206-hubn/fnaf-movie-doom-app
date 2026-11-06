# Offline updater for the optimized test

This Java-only app reconstructs the **separate** optimized FNAF APK from the
phone-confirmed Animation Test without network access. It selects the patch by
entire source APK size and SHA-256 before creating any output. A mismatch is
rejected; source and reconstructed target hashes are verified before the APK
appears in Downloads.

* Source: run 42140465933, SHA-256
  `6872aa42e59cfd66bfceae35cd1c289382f533dd2b91d57ce0d8bf91e7a256a4`,
  package `com.fnaf.moviedoom.animationtest`.
* Target: run 42152144244, commit `67280a3`, SHA-256
  `5047ab19722faa809649251d5eb3e3b0f7705294db4e9d535f8cc28180ff9e7c`,
  package `com.fnaf.moviedoom.optimized`.
* Patch: `repair-optimized.dat` (8,478,540 bytes), SHA-256
  `91ce6be4bb67e90425899c7db3e874e498cfcad88032e0e931621028598875ed`.

The refined build-time generator uses bsdiff only to find byte-identical,
forward-only runs; the device's PCH2 reader needs no network or bsdiff runtime.
The patch was replayed with the Java reader against the actual APKs and the
output compared byte-for-byte to the original signed target. On-device helper,
whole-game scenes, performance, and phone data transfer require separate tests.

No application is uninstalled and no old private data is modified. The target
is a different package: **do not uninstall the old game**. The new app has its
own data and does not automatically inherit saves or progress. The updater can
be removed after use. No Android device FPS or rendering claim is made here.
