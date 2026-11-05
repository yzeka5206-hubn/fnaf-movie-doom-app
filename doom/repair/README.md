# Offline pause-menu and partial-animation updater

This separate Java-only app reconstructs the newer FNAF Menü Testi APK from
one of the two most recent installed menu tests. It selects a PCH2 patch using
the *entire* source APK's size and SHA-256 before creating output, then reads
that source again while reconstructing and verifying the full destination SHA.
A mismatched input/output is rejected and a pending output is deleted. No
network permission is needed and it never deletes or modifies installed apps.

Source APKs:

* run 42098677307, SHA-256 `2b026cbc834b15071231eaf63cbd3b2fab8f0317ae161e979033df3fa067ebef`
* run 42086796345, SHA-256 `e279e8a9e90240e71f2c8049b4b35ecfbf083d510404ad79111ec00d593631fb`

Target: run 42111014994, commit 4109a3d, SHA-256
`f7504084a9d64adb89a9058cdb28a896b1452e8a0fc7188a7684d5d82b8cc74e`.
`repair-current.dat` SHA-256 `ebac17954668eb19f614844078b6a78798f4816d879d56c00dd45ec05766cba9`;
`repair-previous.dat` SHA-256 `b7bfbb3fbcc6e6c54ffdc904c18d8e510e536974948d9c6eb805afd4a7f6cc49`.

The output is written to Downloads; the installed test stays untouched during
preparation. The target has a different debug-signing key, so it cannot be
installed over the old menu test. The player must back up wanted menu-test
saves first, manually remove only that old test, and install the new file.
Uninstalling the test can erase its own data. The separate v7 app is untouched.
No Android phone rendering or input success is claimed by this updater.
