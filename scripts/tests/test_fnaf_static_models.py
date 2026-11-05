#!/usr/bin/env python3
"""Contract for the reversible static model addon (no GPU required)."""
import hashlib
import os
import struct
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSET = ROOT / 'doom/src/main/assets/game/fnaf-model-static-v1.pk3'
ORIGINAL = Path(os.environ.get('FNAF_ORIGINAL_PK3', ROOT / 'doom/src/main/assets/game/FiveNightsAtFreddysDoomMOVIE.pk3'))
MODELS = {
    'Characters/Players/Abby/Abby.iqm': (-9.044, -14.521, 0.081, 9.968, 9.299, 59.882),
    'Characters/Players/Abby/AbbyFP.iqm': (-6.564, -14.520, 0.081, 8.962, 9.299, 31.354),
    'Characters/Players/Abby/AbbyArm.iqm': (-9.410, -24.629, -22.391, 63.523, 6.812, 21.444),
    'Props/Scenery/MikeCar/CarScene2.iqm': (-118.522, -306.939, -0.683, 104.156, 156.781, 190.860),
}
FIELDS = '''version filesize flags num_text ofs_text num_meshes ofs_meshes num_vertexarrays
    num_vertexes ofs_vertexarrays num_triangles ofs_triangles ofs_adjacency num_joints
    ofs_joints num_poses ofs_poses num_anims ofs_anims num_frames num_framechannels
    ofs_frames ofs_bounds num_comment ofs_comment num_extensions ofs_extensions'''.split()


def info(model):
    if model[:16] != b'INTERQUAKEMODEL\0':
        raise AssertionError('not IQM')
    header = dict(zip(FIELDS, struct.unpack_from('<27I', model, 16)))
    arrays = {}
    for i in range(header['num_vertexarrays']):
        kind, _, fmt, dim, offset = struct.unpack_from('<5I', model, header['ofs_vertexarrays'] + i * 20)
        arrays[kind] = (fmt, dim, offset)
    return header, arrays


class StaticModelsTests(unittest.TestCase):
    def test_packaging_and_position_bounds(self):
        sizes = (ROOT / 'doom/src/main/java/com/msa/freedoom/FnafBundledGame.kt').read_text().replace('_', '')
        # The historical artifact remains available for comparison, but is
        # neither extracted nor loaded: it would mask the new skeletons.
        self.assertNotIn('STATICMODELS', sizes)
        self.assertIn('listOf(WAD, PK3, TOUCH, FRAMEMODELS)', sizes)
        utils = (ROOT / 'doom/src/main/java/com/msa/freedoom/Utils.kt').read_text()
        self.assertNotIn('copyBundledAddon(responsibleActivity, fullModDir, "fnaf-model-static-v1.pk3")', utils)
        with zipfile.ZipFile(ASSET) as archive:
            self.assertEqual(set(archive.namelist()), set(MODELS))
            self.assertIsNone(archive.testzip())
            for name, expected in MODELS.items():
                with self.subTest(name=name):
                    model = archive.read(name)
                    h, arrays = info(model)
                    self.assertEqual(h['filesize'], len(model))
                    self.assertEqual(h['version'], 2)
                    self.assertGreater(h['num_joints'], 0)  # animation metadata is kept intact
                    self.assertEqual(arrays[0][:2], (7, 3))
                    self.assertEqual(arrays[5][:2], (1, 4))
                    n = h['num_vertexes']
                    self.assertEqual(model[arrays[5][2]:arrays[5][2] + n * 4], bytes(n * 4))
                    positions = list(struct.iter_unpack('<3f', model[arrays[0][2]:arrays[0][2] + n * 12]))
                    bounds = [min(p[i] for p in positions) for i in range(3)] + [max(p[i] for p in positions) for i in range(3)]
                    for actual, target in zip(bounds, expected):
                        self.assertAlmostEqual(actual, target, delta=0.003)

    @unittest.skipUnless(zipfile.is_zipfile(ORIGINAL), 'Set FNAF_ORIGINAL_PK3 to the real game archive')
    def test_only_geometry_normals_and_weights_changed(self):
        h = hashlib.sha256(ORIGINAL.read_bytes()).hexdigest()
        self.assertEqual(h, 'baf29b1f871aad39aee8895b2ead8edf4f48d9d1615bb1a5e79e98191e1d4ea6')
        with zipfile.ZipFile(ORIGINAL) as original, zipfile.ZipFile(ASSET) as archive:
            for name in MODELS:
                with self.subTest(name=name):
                    source, baked = original.read(name), archive.read(name)
                    h, arrays = info(source)
                    self.assertEqual(len(source), len(baked))
                    # All metadata, meshes, skins, frames, triangle indices and
                    # bone names must be byte-for-byte identical to the original.
                    masked = bytearray(baked)
                    for kind, size in ((0, h['num_vertexes'] * 12), (2, h['num_vertexes'] * 12), (5, h['num_vertexes'] * 4)):
                        at = arrays[kind][2]
                        masked[at:at + size] = source[at:at + size]
                    self.assertEqual(bytes(masked), source)
                    self.assertNotEqual(baked, source)


if __name__ == '__main__':
    unittest.main()
