import sys
import io
import hashlib
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_fnaf_apk as verifier

TEST_SHADER = b"shader-with-skeletal-support"
TEST_SHADER_SHA = hashlib.sha256(TEST_SHADER).hexdigest()
SMALL_ASSETS = {name: (4, magic) for name, (_, magic) in verifier.ASSETS.items()}


@patch.object(verifier, "SKELETAL_SHADER_SHA256", TEST_SHADER_SHA)
class ApkVerifierTest(unittest.TestCase):
    def make_apk(self, directory, *, marker=True, missing=None, bad_magic=None, missing_marker=None, bad_shader=False, bad_native=False, static_dex=False):
        apk = Path(directory) / "app-debug.apk"
        with zipfile.ZipFile(apk, "w") as archive:
            for name, (_, magic) in SMALL_ASSETS.items():
                if name != missing:
                    archive.writestr(name, b"version https://git-lfs.github.com/spec/v1" if name == bad_magic else magic + b"valid-game-content")
            base = io.BytesIO()
            with zipfile.ZipFile(base, "w") as embedded:
                embedded.writestr(verifier.SHADER_MEMBER, b"old shader" if bad_shader else TEST_SHADER)
            archive.writestr("assets/uzdoom.pk3", base.getvalue())
            for name in verifier.NATIVE_LIBRARIES:
                archive.writestr(name, b"\x7fELF" + (b"old engine" if bad_native else verifier.NATIVE_BONE_MARKER))
            markers = b"|".join(m for m in verifier.REQUIRED_DEX_MARKERS if m != missing_marker)
            archive.writestr("classes2.dex", b"dex\n" + (markers if marker else b"old-build") + (b"fnaf-model-static-v1.pk3" if static_dex else b""))
        return apk

    @patch.object(verifier, "ASSETS", SMALL_ASSETS)
    def test_good_apk(self):
        with tempfile.TemporaryDirectory() as temp:
            result = verifier.validate_apk(self.make_apk(temp))
            self.assertEqual(len(result), len(SMALL_ASSETS))

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
    def test_missing_native_menu_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, "marker"):
                verifier.validate_apk(self.make_apk(temp, missing_marker=b"FnafMenuActivity"))

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

    @patch.object(verifier, "ASSETS", SMALL_ASSETS)
    def test_old_shader_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, "shader"):
                verifier.validate_apk(self.make_apk(temp, bad_shader=True))

    @patch.object(verifier, "ASSETS", SMALL_ASSETS)
    def test_old_native_engine_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, "skeletal support"):
                verifier.validate_apk(self.make_apk(temp, bad_native=True))

    @patch.object(verifier, "ASSETS", SMALL_ASSETS)
    def test_legacy_static_override_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, "static model"):
                verifier.validate_apk(self.make_apk(temp, static_dex=True))


if __name__ == "__main__":
    unittest.main()
