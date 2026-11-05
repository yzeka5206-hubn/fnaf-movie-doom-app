#!/usr/bin/env python3
"""Validate the small GLES vertex-animation fallback and its source poses."""
import hashlib
import importlib.util
import os
import struct
import unittest
import zipfile
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
ASSET = ROOT / 'doom/src/main/assets/game/fnaf-model-frames-v1.pk3'
ORIGINAL = Path(os.environ.get('FNAF_ORIGINAL_PK3', ROOT / 'doom/src/main/assets/game/FiveNightsAtFreddysDoomMOVIE.pk3'))
spec = importlib.util.spec_from_file_location('frames', ROOT / 'tools/fnaf-frame-fallback/build.py')
frames = importlib.util.module_from_spec(spec)
spec.loader.exec_module(frames)


def md3_info(data):
    v = struct.unpack_from('<4sI64s9I', data)
    magic, version, name, flags, count, tags, surfaces, skins, of_frames, of_tags, of_surf, end = v
    assert magic == b'IDP3' and version == 15 and end == len(data) and tags == 0
    assert of_frames == 108 and of_tags == of_surf == 108 + count * 56
    result = []
    offset = of_surf
    for _ in range(surfaces):
        m = struct.unpack_from('<4s64s10I', data, offset)
        assert m[0] == b'IDP3' and m[3] == count
        _, _, _, _, shaders, vertices, triangles, t, sh, uv, xyz, last = m
        assert (t, sh, uv, xyz, last) == (108, 108+triangles*12,
            108+triangles*12+shaders*68, 108+triangles*12+shaders*68+vertices*8,
            108+triangles*12+shaders*68+vertices*8+vertices*count*8)
        result.append((offset, vertices, triangles, t, uv, xyz))
        offset += last
    assert offset == end
    return count, result


class FrameModelTests(unittest.TestCase):
    def test_bundle_and_modeldefs(self):
        kotlin = (ROOT / 'doom/src/main/java/com/msa/freedoom/FnafBundledGame.kt').read_text().replace('_', '')
        self.assertIn(f'FRAMEMODELS to {ASSET.stat().st_size}L', kotlin)
        self.assertIn('STATICMODELS, FRAMEMODELS', kotlin)
        self.assertIn('copyBundledAddon(responsibleActivity, fullModDir, "fnaf-model-frames-v1.pk3")',
                      (ROOT / 'doom/src/main/java/com/msa/freedoom/Utils.kt').read_text())
        with zipfile.ZipFile(ASSET) as z:
            self.assertIsNone(z.testzip())
            modeldef = z.read('MODELDEF').decode()
            self.assertEqual(len(z.namelist()), 1 + len(frames.MODELS))
            for model, (source, multiplier, duplicate) in frames.MODELS.items():
                path = str(Path(source).with_name(Path(source).stem + '-fnaf-frames.md3'))
                count, surfaces = md3_info(z.read(path))
                self.assertGreater(count, 20)
                self.assertGreater(len(surfaces), 0)
                self.assertIn('Model ' + model + '\n', modeldef)
                self.assertIn(Path(path).name, modeldef)
            self.assertNotIn('Model Abby\n', modeldef)
            self.assertNotIn('Model Freddy\n', modeldef)  # named SetAnimation is NOT fixed

    @unittest.skipUnless(zipfile.is_zipfile(ORIGINAL), 'Set FNAF_ORIGINAL_PK3 to original game archive')
    def test_samples_match_source_iqm_skinning_and_triangle_geometry(self):
        self.assertEqual(hashlib.sha256(ORIGINAL.read_bytes()).hexdigest(), frames.ORIGINAL_SHA256)
        with zipfile.ZipFile(ORIGINAL) as original, zipfile.ZipFile(ASSET) as addon:
            for name, (path, mult, loop) in frames.MODELS.items():
                with self.subTest(model=name):
                    iqm = original.read(path)
                    h = dict(zip(frames.static.FIELDS, struct.unpack_from('<27I', iqm, 16)))
                    arrays = {}
                    for i in range(h['num_vertexarrays']):
                        kind, _, fmt, dim, offset = struct.unpack_from('<5I', iqm, h['ofs_vertexarrays'] + i*20)
                        arrays[kind] = (fmt, dim, offset)
                    md3_path = str(Path(path).with_name(Path(path).stem + '-fnaf-frames.md3'))
                    data = addon.read(md3_path)
                    count, surfaces = md3_info(data)
                    self.assertEqual(count, h['num_frames'] + int(loop))
                    meshes = [struct.unpack_from('<6I', iqm, h['ofs_meshes'] + i*24) for i in range(h['num_meshes'])]
                    self.assertEqual(len(meshes), len(surfaces))
                    for frame_no in (0, h['num_frames']//2, h['num_frames']-1):
                        baked, _ = frames.static.bake_model(iqm, frame_no, verify_bounds=True)
                        source_pos = np.frombuffer(baked, '<f4', h['num_vertexes']*3, arrays[0][2]).reshape(-1, 3)
                        for mesh, surface in zip(meshes, surfaces):
                            _, _, first, n, first_tri, ntri = mesh
                            off, nverts, triangles, t, uv, xyz = surface
                            self.assertEqual((n, ntri), (nverts, triangles))
                            # Decode MD3 signed 16-bit positions, accounting for our inverse scale.
                            dtype = np.dtype([('xyz','<i2',(3,)),('normal','<u2')])
                            v = np.frombuffer(data, dtype, n, off + xyz + frame_no * n * 8)
                            error = np.abs(v['xyz'].astype(float)/(64*mult) - source_pos[first:first+n])
                            self.assertLessEqual(float(error.max()), 0.5001/(64*mult))
                            source_tri = np.frombuffer(iqm, '<u4', ntri*3, h['ofs_triangles']+first_tri*12).reshape(-1,3)
                            target_tri = np.frombuffer(data, '<u4', ntri*3, off+t).reshape(-1,3)
                            self.assertTrue(np.array_equal(target_tri, source_tri - first))
                    if loop:
                        off, n, _, _, _, xyz = surfaces[0]
                        self.assertEqual(data[off+xyz:off+xyz+n*8], data[off+xyz+(count-1)*n*8:off+xyz+count*n*8])

if __name__ == '__main__':
    unittest.main()
