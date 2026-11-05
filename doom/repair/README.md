# Offline touch-update helper

This branch builds a small Java-only APK. It reads the previously installed
`com.fnaf.moviedoom.menutest` APK, reconstructs the newer GitHub Actions APK
without downloading the full game, and writes it to Downloads. If the installed
APK cannot be read, the player can choose the old APK file instead.

The helper has no network permission and never uninstalls or modifies another
app. The streaming PCH2 patch checks the complete old and new APK SHA-256; a
mismatched or incomplete input/output is rejected and its pending file removed.
The old game (v7) has a different package and must not be removed.

The new debug APK has a different signing certificate. Android will not install
it over the old menu test; the player must back up any wanted menu-test saves
before manually uninstalling that old test, then install the new APK. Uninstalling
the test can remove its own app data. No Xiaomi-device success is claimed.

Source APK: run 42025213047, commit 8cc73bf, SHA-256
`72be818c2d0a686d9b141c3311fb51999ed5f03a745b4b52882b042e24119f27`.
Target APK: run 42086796345, commit 4e6a398, SHA-256
`e279e8a9e90240e71f2c8049b4b35ecfbf083d510404ad79111ec00d593631fb`.
Patch SHA-256:
`93de082f434b07f7c10009c9323faecd49050911111781c90735121cfff32715`.
