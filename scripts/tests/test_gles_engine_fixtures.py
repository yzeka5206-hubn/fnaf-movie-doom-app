#!/usr/bin/env python3
"""Regression tests for the diagnostic engine-room generator and comparator.

Uses a synthetic IQM header for fixture generation; it does NOT replace the
separate real-IQM/GLES engine or target-device tests.
"""
import struct
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from zipfile import ZipFile

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/fnaf-gles-skinning'))
import make_engine_fixture as fixture
from compare_fixture_frames import compare
from test_models import baker


def fake_iqm(clip, first=4, count=30):
    text = clip.encode() + b'\0'
    fields = {name: 0 for name in baker.FIELDS}
    fields.update(version=2, num_text=len(text), ofs_text=124, num_anims=1,
                  ofs_anims=124 + len(text), num_frames=first + count)
    data = (b'INTERQUAKEMODEL\0' + struct.pack('<27I', *(fields[k] for k in baker.FIELDS))
            + text + struct.pack('<IIIfI', 0, first, count, 35.0, 0))
    return data


class EngineFixtureTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.game = self.root / 'game.pk3'
        self.out = self.root / 'fixture'
        self.model, self.clip, _ = fixture.GROUPS['Pepsi'][0]
        with ZipFile(self.game, 'w') as z:
            z.writestr(self.model, fake_iqm(self.clip))
        self.addon = self.root / 'addon.pk3'
        self.md3path = 'Characters/Animatronics/Pepsiman/PepsiMan-fnaf-frames.md3'
        with ZipFile(self.addon, 'w') as z:
            z.writestr(self.md3path, b'test-md3')

    def build(self, *args):
        argv = ['make_engine_fixture.py', str(self.game), 'Pepsi', str(self.out), *map(str, args)]
        with patch.object(sys, 'argv', argv):
            fixture.main()
        return ZipFile(self.out / 'fixture.pk3')

    def test_fixed_index_uses_absolute_clip_frame(self):
        with self.build('15', '--solid-skin', '--frame-index') as z:
            modeldef = z.read('MODELDEF').decode()
            actors = z.read('ZSCRIPT').decode()
            self.assertEqual(modeldef.count('FrameIndex TSTX A 0 19'), 3)
            self.assertNotIn('SetAnimation', actors)
            self.assertNotIn('DECOUPLEDANIMATIONS', actors)
            self.assertNotIn('BaseFrame', modeldef)
            self.assertIn('Characters/Animatronics/Pepsiman/fixture-solid-2.png', z.namelist())

    def test_live_index_advances_sprite_frames(self):
        with self.build('--solid-skin', '--frame-index') as z:
            actors = z.read('ZSCRIPT').decode()
            modeldef = z.read('MODELDEF').decode()
            self.assertIn('TSTX ABCDEFGHIJKLMNOPQRSTUVWXYZ 1;', actors)
            self.assertEqual(modeldef.count('FrameIndex TSTX Z 0 29'), 3)
            self.assertIn('sprites/TSTXZ0.png', z.namelist())

    def test_live_named_animation_does_not_reset_each_tick(self):
        with self.build('--solid-skin') as z:
            actors = z.read('ZSCRIPT').decode()
            self.assertIn('SAF_LOOP|SAF_NOOVERRIDE', actors)
            self.assertIn('+DECOUPLEDANIMATIONS', actors)
            self.assertEqual(z.read('MODELDEF').decode().count(' BaseFrame'), 3)

    def test_actual_addon_bytes_are_used_at_inverse_scale(self):
        with self.build('15', '--solid-skin', '--frame-index', '--pepsi-md3', self.addon) as z:
            modeldef = z.read('MODELDEF').decode()
            self.assertEqual(z.read(self.md3path), b'test-md3')
            self.assertEqual(modeldef.count('PepsiMan-fnaf-frames.md3'), 3)
            self.assertEqual(modeldef.count('Scale 0.3515625 0.3515625 0.3515625'), 3)

    def test_invalid_frame_is_rejected(self):
        with self.assertRaises(ValueError):
            self.build('30', '--frame-index')

    def test_zoom_only_changes_arm_layout(self):
        original = fixture.fixture_models('Nick')
        zoomed = fixture.fixture_models('Nick', zoom_arm=True)
        self.assertEqual(zoomed[:2], original[:2])
        self.assertEqual(zoomed[2][2], 4 * original[2][2])
        self.assertEqual(fixture.map_data(True).count(b'height=160'), 1)
        with self.assertRaises(ValueError):
            fixture.fixture_models('Pepsi', zoom_arm=True)

    def test_static_silhouettes_do_not_pass_motion_check(self):
        first = np.zeros((100, 100, 3), dtype=np.uint8)
        for x, color in [(10, (0, 220, 255)), (40, (25, 245, 25)), (70, (255, 0, 220))]:
            first[20:30, x:x+10] = color
        second = np.roll(first, 3, axis=1)
        a, b = self.root / 'a.png', self.root / 'b.png'
        Image.fromarray(first).save(a)
        Image.fromarray(second).save(b)
        self.assertFalse(any(x['pass'] for x in compare(a, a).values()))
        self.assertTrue(all(x['pass'] for x in compare(a, b).values()))


if __name__ == '__main__':
    unittest.main()
