#!/usr/bin/env python3
"""Compare an engine fixture screenshot to an independent CPU IQM pose.

Usage: verify_fixture_pose.py ORIGINAL_GAME.pk3 Mike 15 screenshot.png
Requires the --solid-skin fixture and its default 640x480 camera. The CPU
reconstructs every vertex of each model's named Walk frame, projects the
triangles for the generated room, and compares each solid-color silhouette.
It is an integration diagnostic, not proof of Android or complete gameplay.
"""
import argparse
import json
from pathlib import Path
from zipfile import ZipFile

import cv2
import numpy as np

from compare_fixture_frames import masks
from make_engine_fixture import GROUPS, ARM_ZOOM_HEIGHT, fixture_models
from test_models import animations, arrays, binding, header, palette


ACTOR_Y = (-125, 0, 125)
ACTOR_HEIGHT = 64
CAMERA_X = -320
CAMERA_EYE_HEIGHT = 41
ACTOR_X = 70
PIXEL_STRETCH = 1.2
FOCAL_LENGTH = 320  # 640-pixel width at a 90 degree field of view


def projected_mask(model_bytes, actor_index, scale, frame, bind_pose=False, actor_height=ACTOR_HEIGHT):
    h = header(model_bytes)
    a = arrays(model_bytes, h)
    vertices = np.asarray(a[0], dtype=np.float64)
    # The engine converts IQM's XYZ to its XZY model-space ordering.
    points = np.column_stack((vertices[:, 0], vertices[:, 2], vertices[:, 1], np.ones(len(vertices))))
    if not bind_pose:
        clip = [x for x in animations(model_bytes, h) if x[0] == 'Walk']
        if len(clip) != 1 or frame < 0 or frame >= clip[0][2]:
            raise ValueError(f'invalid Walk frame {frame}')
        joints, inverse = binding(model_bytes, h)
        matrices = palette(model_bytes, h, joints, inverse, clip[0][1] + frame)
        selectors = a[4]
        weights = np.asarray(a[5], dtype=np.float64)
        sums = weights.sum(axis=1)
        weights /= np.where(sums > 0, sums, 1)[:, None]
        transformed = np.zeros_like(points)
        for k in range(4):
            transformed += np.einsum('nij,nj->ni', matrices[selectors[:, k]], points) * weights[:, k, None]
        transformed[sums == 0] = points[sums == 0]
        points = transformed
    # UDMF actor yaw=180, MODELDEF AngleOffset=90. The corresponding engine
    # model matrix rotates -270 degrees around vertical and applies a 1/1.2
    # model stretch; the viewpoint then applies the room's pixel stretch.
    x, vertical, y = points[:, :3].T
    depth = ACTOR_X - CAMERA_X + y * scale
    if np.any(depth <= 0):
        raise ValueError('fixture geometry crosses the camera plane')
    sx = 320 - (ACTOR_Y[actor_index] - x * scale) * FOCAL_LENGTH / depth
    sy = 240 - (vertical * scale + (actor_height - CAMERA_EYE_HEIGHT) * PIXEL_STRETCH) * FOCAL_LENGTH / depth
    projected = np.rint(np.stack((sx, sy), axis=1) - 0.5).astype(np.int32)
    indices = np.frombuffer(model_bytes, dtype='<u4', count=h['num_triangles'] * 3,
                            offset=h['ofs_triangles']).reshape((-1, 3))
    if indices.max() >= len(points):
        raise ValueError('triangle index is out of bounds')
    mask = np.zeros((480, 640), dtype=np.uint8)
    cv2.fillPoly(mask, list(projected[indices]), 1)
    return mask.astype(bool)


def compare_pose(game, group, frame, screenshot, bind_pose=False, threshold=0.55, zoom_arm=False):
    actual = list(masks(screenshot).values())
    if actual[0].shape != (480, 640):
        raise ValueError('expected a 640x480 fixture screenshot')
    output = {}
    with ZipFile(game) as archive:
        for i, (model, _clip, scale) in enumerate(fixture_models(group, zoom_arm)):
            height = ARM_ZOOM_HEIGHT if zoom_arm and i == 2 else ACTOR_HEIGHT
            predicted = projected_mask(archive.read(model), i, scale, frame, bind_pose, actor_height=height)
            target = actual[i]
            intersection = int(np.logical_and(predicted, target).sum())
            union = int(np.logical_or(predicted, target).sum())
            iou = intersection / union if union else 0.0
            output[f'actor_{i}'] = {
                'model': model, 'expected_pixels': int(predicted.sum()),
                'actual_pixels': int(target.sum()), 'intersection_over_union': round(iou, 4),
                'pass': bool(iou >= threshold),
            }
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('game', type=Path)
    parser.add_argument('group', choices=sorted(GROUPS))
    parser.add_argument('frame', type=int)
    parser.add_argument('screenshot', type=Path)
    parser.add_argument('--bind-pose', action='store_true', help='calibrate against the unpatched ES T-pose')
    parser.add_argument('--zoom-arm', action='store_true', help='fixture used the diagnostic --zoom-arm layout')
    parser.add_argument('--threshold', type=float, default=0.55)
    args = parser.parse_args()
    result = compare_pose(args.game, args.group, args.frame, args.screenshot, args.bind_pose, args.threshold, args.zoom_arm)
    print(json.dumps(result, indent=2))
    if not all(v['pass'] for v in result.values()):
        raise SystemExit('FAIL: at least one actor silhouette disagrees with its CPU IQM pose')
    print('PASS: each fixture silhouette agrees with its CPU-projected IQM pose')


if __name__ == '__main__':
    main()
