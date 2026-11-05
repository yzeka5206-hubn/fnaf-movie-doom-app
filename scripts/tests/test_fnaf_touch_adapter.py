#!/usr/bin/env python3
"""Basic consistency checks for the shipped (not test-instrumented) adapter."""
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/fnaf-touch"
ASSET = ROOT / "doom/src/main/assets/game/fnaf-touch-v1.pk3"


class TouchAdapterTests(unittest.TestCase):
    def test_bundled_pk3_exactly_matches_sources(self):
        sizes_source = (ROOT / "doom/src/main/java/com/msa/freedoom/FnafBundledGame.kt").read_text()
        self.assertIn(f"TOUCH to {ASSET.stat().st_size}L", sizes_source)
        with zipfile.ZipFile(ASSET) as archive:
            self.assertEqual(set(archive.namelist()), {"ZSCRIPT", "MAPINFO"})
            for name in ("ZSCRIPT", "MAPINFO"):
                self.assertEqual(archive.read(name), (SOURCE / name).read_bytes())
            script = archive.read("ZSCRIPT").decode("utf-8")
            self.assertIn('class FnafDirectTouchHandler', script)
            self.assertIn('FnafHandleTouch(e.Args[0], e.Args[1], e.Args[2])', script)
            self.assertIn('EventHandler.SendNetworkEvent("NetMouse", nx, ny)', script)
            self.assertIn('EventHandler.SendNetworkEvent("NetClick")', script)
            self.assertNotIn('.MouseEvent(', script)
            self.assertNotIn('.MenuEvent(', script)
            self.assertIn('FnafTouchAck', script)
            self.assertIn('fnafMouseGuard', script)
            self.assertNotIn('FNAF_AUTOTEST', script)
            self.assertNotIn('REAL_SELECTION_', script)


if __name__ == "__main__":
    unittest.main()
