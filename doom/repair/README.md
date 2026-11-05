# One-time offline menu repair helper

This branch builds only the small Java updater, not the game. It has no network
permission. It verifies both the old installed test APK and its repaired output
against SHA-256 before publishing a file to Downloads. The user controls all
uninstallation and installation steps; the game/v7 package is never modified.

Old test APK SHA-256: `dde4c9632c44fe2600577e6b35670ccfc1bde18ea5305f47ccd07b9f4e939c88`.
Target test APK SHA-256: `c97c8f517ec7fd7f76e41cfaaa4d5787b21e499facbd763b31c6dda1cbafc6d2`.
Target: `original-menu-prototype` commit `647fb49`, Actions run `42023788658`.

The embedded `FNAFPCH2` streaming patch references byte-identical ZIP payloads
from the old APK and includes only changed headers, code and signatures. It can
be regenerated from these exact signed APKs with `scripts/make-apk-repair-delta.py`.
`tests/Test.java` tests shifted/unequal sizes and malformed input; passing exact
old, patch and output paths additionally tests the entire APK reconstruction.
