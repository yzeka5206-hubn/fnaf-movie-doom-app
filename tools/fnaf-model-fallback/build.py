#!/usr/bin/env python3
"""Make reversible, static GLES fallbacks from the user's original game PK3.

Requires numpy.  Only positions, normals and blend weights are changed; model
names, material paths and animation metadata remain intact.  Zero weights
make modern GPU renderers use the baked vertices without skinning a second
time; GLES 2 already renders these unskinned vertices.

Usage: python3 tools/fnaf-model-fallback/build.py /path/to/original-game.pk3
Remove this whole addon when real skeletal animation is supported.
"""
from __future__ import annotations

import hashlib
import struct
import sys
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "doom/src/main/assets/game/fnaf-model-static-v1.pk3"
ORIGINAL_SHA256 = "baf29b1f871aad39aee8895b2ead8edf4f48d9d1615bb1a5e79e98191e1d4ea6"
MODELS = {
    "Characters/Players/Abby/Abby.iqm": 0,
    "Characters/Players/Abby/AbbyFP.iqm": 0,
    "Characters/Players/Abby/AbbyArm.iqm": 0,
    # The opening OUTSIDE ACS script spawns MikeNewCarScene, whose model is
    # CarScene2. Its terminal frame places the car near the scene's actors.
    "Props/Scenery/MikeCar/CarScene2.iqm": 700,
}
FIELDS = """version filesize flags num_text ofs_text num_meshes ofs_meshes
    num_vertexarrays num_vertexes ofs_vertexarrays num_triangles ofs_triangles
    ofs_adjacency num_joints ofs_joints num_poses ofs_poses num_anims ofs_anims
    num_frames num_framechannels ofs_frames ofs_bounds num_comment ofs_comment
    num_extensions ofs_extensions""".split()


def matrix(t: np.ndarray, q: np.ndarray, s: np.ndarray) -> np.ndarray:
    length = np.linalg.norm(q)
    if not np.isfinite(length) or length < 1e-12:
        raise ValueError("Invalid model quaternion")
    x, y, z, w = q / length
    rot = np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])
    result = np.eye(4)
    result[:3, :3] = rot @ np.diag(s)
    result[:3, 3] = t
    return result


def bake_model(source: bytes, frame: int, verify_bounds: bool) -> tuple[bytes, np.ndarray]:
    if source[:16] != b"INTERQUAKEMODEL\x00":
        raise ValueError("Not an IQM file")
    h = dict(zip(FIELDS, struct.unpack_from("<27I", source, 16)))
    if (h["version"] != 2 or h["filesize"] != len(source)
            or not (0 <= frame < h["num_frames"])
            or h["num_joints"] != h["num_poses"] or not h["num_joints"]):
        raise ValueError("Unexpected IQM version, size, frame or skeleton")

    joints: list[tuple[int, np.ndarray]] = []
    for i in range(h["num_joints"]):
        record = struct.unpack_from("<Ii10f", source, h["ofs_joints"] + i * 48)
        parent = record[1]
        if parent >= i or parent < -1:
            raise ValueError("Invalid joint hierarchy")
        local = matrix(np.array(record[2:5]), np.array(record[5:9]), np.array(record[9:12]))
        joints.append((parent, joints[parent][1] @ local if parent >= 0 else local))

    poses: list[tuple[int, np.ndarray]] = []
    offset = h["ofs_frames"] + frame * h["num_framechannels"] * 2
    frame_end = offset + h["num_framechannels"] * 2
    for i in range(h["num_poses"]):
        record = struct.unpack_from("<iI20f", source, h["ofs_poses"] + i * 88)
        parent, mask = record[:2]
        if parent != joints[i][0]:
            raise ValueError("Pose and joint hierarchy differ")
        values = np.array(record[2:12])
        scales = np.array(record[12:22])
        for channel in range(10):
            if mask & (1 << channel):
                values[channel] += struct.unpack_from("<H", source, offset)[0] * scales[channel]
                offset += 2
        local = matrix(values[:3], values[3:7], values[7:])
        poses.append((parent, poses[parent][1] @ local if parent >= 0 else local))
    if offset != frame_end:
        raise ValueError("Frame channel count does not match poses")
    transforms = np.stack([pose[1] @ np.linalg.inv(joint[1]) for pose, joint in zip(poses, joints)])

    arrays = {}
    for i in range(h["num_vertexarrays"]):
        kind, flags, fmt, dim, at = struct.unpack_from("<5I", source, h["ofs_vertexarrays"] + i * 20)
        if kind in arrays:
            raise ValueError("Duplicate vertex array")
        arrays[kind] = (fmt, dim, at)
    if any(arrays.get(kind, ())[:2] != expected for kind, expected in {
        0: (7, 3), 2: (7, 3), 4: (1, 4), 5: (1, 4)
    }.items()):
        raise ValueError("Unsupported position, normal or skinning format")

    def array(kind: int, dtype: str) -> np.ndarray:
        _, dim, at = arrays[kind]
        return np.frombuffer(source, dtype=dtype, count=h["num_vertexes"] * dim,
                             offset=at).reshape((-1, dim))

    positions = array(0, "<f4")
    normals = array(2, "<f4")
    indices = array(4, "u1")
    weights = array(5, "u1").astype("f8")
    sums = weights.sum(axis=1)
    if np.any((weights > 0) & (indices >= len(joints))):
        raise ValueError("Skin references a nonexistent joint")
    weights /= np.where(sums > 0, sums, 1)[:, None]
    homogeneous = np.column_stack((positions, np.ones(len(positions))))
    baked = np.zeros_like(homogeneous)
    baked_normals = np.zeros((len(positions), 3))
    for k in range(4):
        indices_k = np.where(weights[:, k] > 0, indices[:, k], 0)
        transform = transforms[indices_k]
        baked += (transform @ homogeneous[:, :, None])[:, :, 0] * weights[:, k, None]
        baked_normals += (transform[:, :3, :3] @ normals[:, :, None])[:, :, 0] * weights[:, k, None]
    # A few meshes have no skeletal weights at all: the original shader leaves
    # them unchanged instead of multiplying them by an all-zero matrix.
    baked[sums == 0] = homogeneous[sums == 0]
    baked_normals[sums == 0] = normals[sums == 0]
    normal_lengths = np.linalg.norm(baked_normals, axis=1)
    if np.any(normal_lengths < 1e-12) or not np.all(np.isfinite(baked)):
        raise ValueError("Invalid baked coordinates or normals")
    baked_normals /= normal_lengths[:, None]
    bounds = np.concatenate((baked[:, :3].min(axis=0), baked[:, :3].max(axis=0)))
    if verify_bounds:
        declared = np.array(struct.unpack_from("<6f", source, h["ofs_bounds"] + frame * 32))
        if not np.allclose(bounds, declared, atol=0.002, rtol=0):
            raise ValueError(f"Baked bounds do not match original frame: {bounds} vs {declared}")

    result = bytearray(source)
    for kind, contents in ((0, baked[:, :3].astype("<f4").tobytes()),
                           (2, baked_normals.astype("<f4").tobytes()),
                           (5, bytes(h["num_vertexes"] * 4))):
        at = arrays[kind][2]
        result[at:at + len(contents)] = contents
    return bytes(result), bounds


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    original = Path(sys.argv[1])
    digest = hashlib.sha256(original.read_bytes()).hexdigest()
    if digest != ORIGINAL_SHA256:
        raise SystemExit(f"Unexpected original game archive SHA-256: {digest}")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(original) as game, ZipFile(OUTPUT, "w", compression=ZIP_DEFLATED, compresslevel=9) as addon:
        for name, frame in MODELS.items():
            model, bounds = bake_model(game.read(name), frame, verify_bounds=name.startswith("Characters/"))
            # This validates that the formerly distant car's weighted geometry
            # actually landed close to the spawn point. An unweighted Mike mesh
            # in this IQM correctly remains unchanged.
            if name.endswith("CarScene2.iqm") and (bounds[0] < -500 or bounds[3] > 500):
                raise ValueError(f"Static opening car is still too far away: {bounds}")
            info = ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            addon.writestr(info, model, compress_type=ZIP_DEFLATED, compresslevel=9)
            print(name, "frame", frame, "bounds", np.round(bounds, 3))
    print(OUTPUT, OUTPUT.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
