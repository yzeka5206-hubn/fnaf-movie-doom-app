# Offline updater for the skeletal-animation test

This Java-only app reconstructs the **separate** FNAF Animasyon Testi APK from
one of three known installed FNAF Menü Testi versions, without network access.
It selects a PCH2 patch using the entire source APK's size and SHA-256 *before*
creating any output. It then reads that source again while reconstructing the
entire destination, and verifies both source and target SHA-256. A mismatch is
rejected and any pending output is deleted.

Supported source APKs:

* run 42111014994: `f7504084a9d64adb89a9058cdb28a896b1452e8a0fc7188a7684d5d82b8cc74e`
* run 42098677307: `2b026cbc834b15071231eaf63cbd3b2fab8f0317ae161e979033df3fa067ebef`
* run 42086796345: `e279e8a9e90240e71f2c8049b4b35ecfbf083d510404ad79111ec00d593631fb`

Target: run 42140465933, commit 2529684, SHA-256
`6872aa42e59cfd66bfceae35cd1c289382f533dd2b91d57ce0d8bf91e7a256a4`,
package `com.fnaf.moviedoom.animationtest`.

Built-in PCH2 patch SHA-256:

* `repair-current.dat`: `e65442609a97f027b721a6d4e6bdee6ff91814ef75c4b821abc7d4c632c6c693`
* `repair-previous.dat`: `83a53320099a5aae126d3b9fe22f02eb20bec3e4f018b7ff919706342c0c1b6c`
* `repair-older.dat`: `699d07a855e97901241ca0fbc48747dcbf313922b80ddb232377c6e59ad5fc73`

The refined build-time generator uses bsdiff only to find byte-identical,
forward-only runs; the on-device reader remains the same simple PCH2 engine.
All three patches were replayed against the real APKs and compared byte for
byte with the target. The output is published to Downloads only after SHA
verification. No installed application is deleted or modified. The target is a
different package and installs alongside the old app, so **do not uninstall the
old game**; its files and saves remain there. The new test starts with its own
data and does not automatically inherit saves. The updater itself has its own
package name, distinct from earlier updater apps, and can be removed after use.
No Android phone rendering or input success is claimed by this updater.
