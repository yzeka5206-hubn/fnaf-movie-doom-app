#!/usr/bin/env python3
"""Bake a small, audited subset of the game's IQM FrameIndex animations to MD3.

The Android GLES 2 renderer cannot skin IQM joints, but it can interpolate MD3
vertex frames. This tool keeps the game's own actor/state mappings and bakes
those exact poses into a small, separate and reversible replacement MODELDEF.
It deliberately does NOT claim to fix named SetAnimation clips such as Freddy.
Requires NumPy. Supply the unmodified game archive as the sole argument.
"""
from __future__ import annotations
import hashlib
import importlib.util
import re
import struct
import sys
import zipfile
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('fnaf_static_bake', ROOT / 'tools/fnaf-model-fallback/build.py')
assert spec and spec.loader
static = importlib.util.module_from_spec(spec)
spec.loader.exec_module(static)

ORIGINAL_SHA256 = 'baf29b1f871aad39aee8895b2ead8edf4f48d9d1615bb1a5e79e98191e1d4ea6'
# name: (original IQM, vertex precision multiplier, optional loop duplicate).
# Quantised MD3 positions cover +/-512 units; inverse MODELDEF scale restores
# size. Higher multipliers improve precision without overflowing int16.
MODELS = {
    'PepsiMan': ('Characters/Animatronics/Pepsiman/PepsiMan.iqm', 128, True),
    'SourceDoor': ('Props/Scenery/Door/Door.iqm', 2, False),
    'SourceLever': ('Props/Scenery/LeverSwitch/SourceLever.iqm', 16, False),
    'ZWallFan2': ('Props/Scenery/wallfan/M_wallfan.iqm', 64, False),
    'Gate': ('Props/Scenery/Gate/gate.iqm', 2, False),
}
OUTPUT = ROOT / 'doom/src/main/assets/game/fnaf-model-frames-v1.pk3'


def cstring(s: str, size: int) -> bytes:
    b = s.encode('utf-8')
    if len(b) >= size:
        raise ValueError(f'string too long for MD3: {s}')
    return b.ljust(size, b'\0')


def iqm_text(blob: bytes, h: dict, offset: int) -> str:
    start = h['ofs_text'] + offset
    end = blob.find(b'\0', start, h['ofs_text'] + h['num_text'])
    if end < 0:
        raise ValueError('unterminated IQM text')
    return blob[start:end].decode('utf-8')


def encode_normals(normals: np.ndarray) -> np.ndarray:
    length = np.linalg.norm(normals, axis=1)
    if np.any(length < 1e-7):
        raise ValueError('zero-length normal')
    n = normals / length[:, None]
    lat = np.rint(np.arctan2(n[:, 1], n[:, 0]) * (128.0 / np.pi)).astype(np.int32) & 255
    lng = np.rint(np.arccos(np.clip(n[:, 2], -1, 1)) * (128.0 / np.pi)).astype(np.int32) & 255
    return ((lat << 8) | lng).astype('<u2')


def bake_md3(blob: bytes, model_name: str, multiplier: int, loop_duplicate: bool) -> bytes:
    if blob[:16] != b'INTERQUAKEMODEL\0':
        raise ValueError('not an IQM')
    h = dict(zip(static.FIELDS, struct.unpack_from('<27I', blob, 16)))
    if h['version'] != 2 or h['filesize'] != len(blob) or h['num_frames'] < 2:
        raise ValueError('unexpected IQM header')
    arrays = {}
    for i in range(h['num_vertexarrays']):
        kind, _, fmt, dim, offset = struct.unpack_from('<5I', blob, h['ofs_vertexarrays'] + 20*i)
        arrays[kind] = (fmt, dim, offset)
    if arrays[0][:2] != (7, 3) or arrays[1][:2] != (7, 2) or arrays[2][:2] != (7, 3):
        raise ValueError('unexpected IQM vertex format')
    nv = h['num_vertexes']
    texcoords = np.frombuffer(blob, dtype='<f4', count=nv*2, offset=arrays[1][2]).reshape(nv, 2)
    triangles = np.frombuffer(blob, dtype='<u4', count=h['num_triangles']*3, offset=h['ofs_triangles']).reshape(-1, 3)
    meshes = [struct.unpack_from('<6I', blob, h['ofs_meshes'] + 24*i) for i in range(h['num_meshes'])]

    # One GPU vertex frame per original IQM frame; no original data is deleted.
    poses, normals, frame_data = [], [], bytearray()
    frames = list(range(h['num_frames'])) + ([0] if loop_duplicate else [])
    for frame_no in frames:
        baked, _ = static.bake_model(blob, frame_no, verify_bounds=True)
        p = np.frombuffer(baked, dtype='<f4', count=nv*3, offset=arrays[0][2]).reshape(nv, 3)
        n = np.frombuffer(baked, dtype='<f4', count=nv*3, offset=arrays[2][2]).reshape(nv, 3)
        q = np.rint(p * (multiplier * 64.0))
        if not np.all(np.isfinite(q)) or np.any(q < -32768) or np.any(q > 32767):
            raise ValueError(f'{model_name} frame {frame_no}: MD3 position overflow')
        poses.append(q.astype('<i2'))
        normals.append(encode_normals(n))
        scaled = p * multiplier
        low, high = scaled.min(axis=0), scaled.max(axis=0)
        radius = np.linalg.norm(scaled, axis=1).max()
        frame_data += struct.pack('<10f16s', *low, *high, 0, 0, 0, radius, cstring(f'f{len(poses)-1}', 16))

    surfaces = bytearray()
    for mesh_no, (name_ofs, mat_ofs, first_v, count_v, first_t, count_t) in enumerate(meshes):
        if first_v + count_v > nv or first_t + count_t > h['num_triangles']:
            raise ValueError('mesh outside IQM geometry')
        inds = triangles[first_t:first_t+count_t].astype(np.int64) - first_v
        if np.any(inds < 0) or np.any(inds >= count_v):
            raise ValueError('mesh triangle references outside its vertex range')
        name, material = iqm_text(blob, h, name_ofs), iqm_text(blob, h, mat_ofs)
        tri_bytes = inds.astype('<u4').tobytes()
        shader_bytes = struct.pack('<64sI', cstring(material, 64), 0) if material else b''
        tex_bytes = texcoords[first_v:first_v+count_v].astype('<f4').tobytes()
        vertices = bytearray()
        vertex_dtype = np.dtype([('xyz', '<i2', (3,)), ('normal', '<u2')])
        for p, n in zip(poses, normals):
            v = np.empty(count_v, dtype=vertex_dtype)
            v['xyz'], v['normal'] = p[first_v:first_v+count_v], n[first_v:first_v+count_v]
            vertices += v.tobytes()
        of_tri = 108
        of_shader = of_tri + len(tri_bytes)
        of_uv = of_shader + len(shader_bytes)
        of_vertex = of_uv + len(tex_bytes)
        of_end = of_vertex + len(vertices)
        surfaces += struct.pack('<4s64s10I', b'IDP3', cstring(name or f'mesh{mesh_no}', 64),
            0, len(frames), int(bool(material)), count_v, count_t,
            of_tri, of_shader, of_uv, of_vertex, of_end)
        surfaces += tri_bytes + shader_bytes + tex_bytes + vertices
    of_frames = 108
    of_tags = of_frames + len(frame_data)
    of_surfaces = of_tags  # no tags
    of_end = of_surfaces + len(surfaces)
    header = struct.pack('<4sI64s9I', b'IDP3', 15, cstring(model_name, 64),
        0, len(frames), 0, len(meshes), 0, of_frames, of_tags, of_surfaces, of_end)
    result = header + frame_data + surfaces
    assert len(result) == of_end
    return result


def replacement_modeldef(original: str, model: str, source_path: str, multiplier: int, num_frames: int) -> str:
    match = re.search(r'(?ims)^Model\s+' + re.escape(model) + r'\s*\{(.*?)\}', original)
    if not match:
        raise ValueError(f'missing original MODELDEF: {model}')
    body = match.group(1)
    basename = source_path.rsplit('/', 1)[-1]
    md3name = Path(basename).stem + '-fnaf-frames.md3'
    body, count = re.subn(r'(?im)(\bMODEL\s+0\s+")' + re.escape(basename) + r'(")',
                          lambda m: m.group(1) + md3name + m.group(2), body)
    if count != 1:
        raise ValueError(f'model filename does not match: {model}')
    def scaled(m):
        values = [float(m.group(i)) / multiplier for i in (1, 2, 3)]
        return 'Scale ' + ' '.join(format(v, '.10g') for v in values)
    body, count = re.subn(r'(?im)^\s*Scale\s+([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)', scaled, body)
    if count != 1:
        raise ValueError(f'model scale missing: {model}')
    indices = [int(x) for x in re.findall(r'(?im)^\s*FrameIndex\s+\S+\s+\S+\s+0\s+(\d+)', body)]
    if not indices or min(indices) < 0 or max(indices) >= num_frames:
        raise ValueError(f'original frame indices outside converted model: {model}')
    return 'Model ' + model + '\n{' + body + '\n}\n'


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit(f'Usage: {sys.argv[0]} original-game.pk3')
    source = Path(sys.argv[1])
    if hashlib.sha256(source.read_bytes()).hexdigest() != ORIGINAL_SHA256:
        raise ValueError('original game archive SHA-256 mismatch')
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(OUTPUT, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as out:
        modeldef = original.read('ModelDef').decode('utf-8-sig')
        defs = ['// Reversible MD3 vertex-frame replacements for GLES 2.\n']
        entries = []
        for name, (path, multiplier, duplicate) in MODELS.items():
            blob = original.read(path)
            header = dict(zip(static.FIELDS, struct.unpack_from('<27I', blob, 16)))
            md3 = bake_md3(blob, name, multiplier, duplicate)
            output_path = str(Path(path).with_name(Path(path).stem + '-fnaf-frames.md3'))
            entries.append((output_path, md3))
            defs.append(replacement_modeldef(modeldef, name, path, multiplier, header['num_frames'] + int(duplicate)))
            print(f'{name}: {header["num_frames"]} IQM frames -> {len(md3):,} byte MD3 ({output_path})')
        entries.insert(0, ('MODELDEF', '\n'.join(defs).encode('utf-8')))
        for name, data in entries:
            info = zipfile.ZipInfo(name, (2024, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            out.writestr(info, data, compresslevel=9)
    print(f'{OUTPUT}: {OUTPUT.stat().st_size:,} bytes sha256={hashlib.sha256(OUTPUT.read_bytes()).hexdigest()}')

if __name__ == '__main__':
    main()
