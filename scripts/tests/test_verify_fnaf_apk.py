import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_fnaf_apk as verifier

SMALL_ASSETS = {name: (4, magic) for name, (_, magic) in verifier.ASSETS.items()}


class ApkVerifierTest(unittest.TestCase):
    def make_apk(self, directory, *, marker=True, missing=None, bad_magic=None, missing_marker=None):
        apk = Path(directory) / "app-debug.apk"
        with zipfile.ZipFile(apk, "w") as archive:
            for name, (_, magic) in SMALL_ASSETS.items():
                if name != missing:
                    archive.writestr(name, b"version https://git-lfs.github.com/spec/v1" if name == bad_magic else magic + b"valid-game-content")
            markers = b"|".join(m for m in verifier.REQUIRED_DEX_MARKERS if m != missing_marker)
            archive.writestr("classes2.dex", b"dex\n" + (markers if marker else b"old-build"))
        return apk

    @patch.object(verifier, "ASSETS", SMALL_ASSETS)
    def test_good_apk(self):
        with tempfile.TemporaryDirectory() as temp:
            result = verifier.validate_apk(self.make_apk(temp))
            self.assertEqual(len(result), 3)

    @patch.object(verifier, "ASSETS", SMALL_ASSETS)
    def test_stale_apk_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, "marker"):
                verifier.validate_apk(self.make_apk(temp, marker=False))

    @patch.object(verifier, "ASSETS", SMALL_ASSETS)
    def test_incomplete_control_build_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, "marker"):
                verifier.validate_apk(self.make_apk(temp, missing_marker=b"forwardSoftEnter"))

    @patch.object(verifier, "ASSETS", SMALL_ASSETS)
    def test_missing_game_asset_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, "missing"):
                verifier.validate_apk(self.make_apk(temp, missing=next(iter(SMALL_ASSETS))))

    @patch.object(verifier, "ASSETS", SMALL_ASSETS)
    def test_lfs_pointer_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, "wrong file format"):
                verifier.validate_apk(self.make_apk(temp, bad_magic=next(iter(SMALL_ASSETS))))


if __name__ == "__main__":
    unittest.main()
