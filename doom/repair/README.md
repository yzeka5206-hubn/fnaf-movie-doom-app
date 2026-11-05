# One-time offline menu repair helper

This branch builds only the small Java updater, not the game. It has no network
permission. It verifies both the old installed test APK and its repaired output
against SHA-256 before publishing a file to Downloads. The user controls all
uninstallation and installation steps; the game/v7 package is never modified.

Old test APK SHA-256: `dde4c9632c44fe2600577e6b35670ccfc1bde18ea5305f47ccd07b9f4e939c88`.
Target test APK SHA-256: `72be818c2d0a686d9b141c3311fb51999ed5f03a745b4b52882b042e24119f27`.
Target: `original-menu-prototype` commit `8cc73bf`, Actions run `42025213047`.

The embedded `FNAFPCH2` streaming patch references byte-identical ZIP payloads
from the old APK and includes only changed headers, code and signatures. It can
be regenerated from these exact signed APKs with `scripts/make-apk-repair-delta.py`.
`tests/Test.java` tests shifted/unequal sizes and malformed input; passing exact
old, patch and output paths additionally tests the entire APK reconstruction.
