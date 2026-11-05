#!/usr/bin/env python3
"""Exercise solid-skin fixture poses through a patched desktop GLES engine.

This requires an X11 display, ImageMagick's import, xdotool, a Freedoom IWAD,
and pre-generated --solid-skin fixed poses with nodes. It does not test an APK,
phone, original game logic, every animation or anatomical correctness.

Example:
 DISPLAY=:91 python3 run_engine_fixtures.py /path/to/uzdoom /path/to/freedoom2.wad \
   /path/to/solid-fixtures /path/to/results Mike Vanessa
"""
import argparse
import json
import os
import signal
import subprocess
import time
from pathlib import Path

import numpy as np
from PIL import Image
from compare_fixture_frames import compare, masks


def stop(proc):
    if proc.poll() is None:
        try:
            os.killpg(proc.pid, signal.SIGTERM)
            proc.wait(timeout=5)
        except (ProcessLookupError, subprocess.TimeoutExpired):
            try:
                os.killpg(proc.pid, signal.SIGKILL)
                proc.wait(timeout=5)
            except ProcessLookupError:
                pass


def capture(engine, iwad, fixture, output, group, frame, timeout):
    result = output / f'{group}-{frame}'
    result.mkdir(parents=True, exist_ok=True)
    log = result / 'engine.log'
    stdout = result / 'stdout.log'
    screenshot = result / 'frame.png'
    config = result / 'config.ini'
    log.unlink(missing_ok=True)
    screenshot.unlink(missing_ok=True)
    env = os.environ.copy()
    env.setdefault('LIBGL_ALWAYS_SOFTWARE', '1')
    env.setdefault('LP_NUM_THREADS', '2')
    env.setdefault('ALSOFT_DRIVERS', 'null')
    cmd = [str(engine), '-config', str(config), '-iwad', str(iwad), '-file',
           str(fixture / 'fixture-nodes.wad'), str(fixture / 'fixture.pk3'),
           '-nosound', '-nomusic', '-window', '+set', 'gl_es', '1',
           '+set', 'vid_preferbackend', '2', '+set', 'vid_fullscreen', '0',
           '+set', 'win_w', '640', '+set', 'win_h', '480',
           '+set', 'vid_defwidth', '640', '+set', 'vid_defheight', '480',
           '+set', 'r_drawplayersprites', '0', '+set', 'screenblocks', '12',
           '+logfile', str(log), '+map', 'MAP01']
    with stdout.open('wb') as out:
        proc = subprocess.Popen(cmd, cwd=engine.parent, env=env, stdout=out,
                                stderr=subprocess.STDOUT, start_new_session=True)
        try:
            deadline = time.monotonic() + timeout
            last_message = 'waiting for MAP01 and a rendered actor'
            while time.monotonic() < deadline:
                if proc.poll() is not None:
                    raise RuntimeError(f'engine exited with {proc.returncode}: {stdout.read_text(errors="replace")[-3000:]}')
                text = log.read_text(errors='replace') if log.exists() else ''
                if any(s in text for s in ('VM execution aborted', 'Could not find animation', 'Script error', 'Fatal error')):
                    raise RuntimeError(f'engine reported a fixture error: {text[-2000:]}')
                if 'MAP01 -' in text:
                    if 'FNAF skeletal GLES palette: enabled' not in text or 'OpenGL ES 3.' not in text:
                        raise RuntimeError('expected patched ES3 palette was not enabled')
                    search = subprocess.run(['xdotool', 'search', '--onlyvisible', '--pid', str(proc.pid)],
                                            text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
                    windows = [w for w in search.stdout.splitlines() if w.isdigit()]
                    if windows:
                        run = subprocess.run(['import', '-window', windows[-1], str(screenshot)],
                                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                        if run.returncode == 0 and screenshot.exists():
                            im = Image.open(screenshot)
                            actor_areas = [int(m.sum()) for m in masks(screenshot).values()]
                            if im.size == (640, 480) and max(actor_areas) >= 30:
                                return screenshot
                            last_message = f'window {im.size}, actor pixels {actor_areas}'
                time.sleep(.5)  # allow the renderer to produce its first frame
            raise TimeoutError(f'{group} frame {frame}: {last_message}; log tail: {text[-1000:]}')
        finally:
            stop(proc)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('engine', type=lambda p: Path(p).resolve())
    parser.add_argument('iwad', type=lambda p: Path(p).resolve())
    parser.add_argument('fixtures', type=lambda p: Path(p).resolve())
    parser.add_argument('output', type=lambda p: Path(p).resolve())
    parser.add_argument('groups', nargs='+')
    parser.add_argument('--timeout', type=int, default=90)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    all_results = {}
    for group in args.groups:
        try:
            frames = [capture(args.engine, args.iwad, args.fixtures / f'{group}-{frame}',
                              args.output, group, frame, args.timeout) for frame in (0, 15)]
            result = compare(*frames)
            all_results[group] = result
            good = all(info['pass'] for info in result.values())
            print(f'{group}: {"PASS" if good else "FAIL"} {json.dumps(result)}', flush=True)
        except Exception as exc:
            all_results[group] = {'error': str(exc)}
            print(f'{group}: ERROR {exc}', flush=True)
        (args.output / 'summary.json').write_text(json.dumps(all_results, indent=2))
    if any('error' in v or not all(i['pass'] for i in v.values()) for v in all_results.values()):
        raise SystemExit('One or more fixture comparisons failed')
    print('PASS: all selected three-actor fixtures changed between two fixed Walk poses')


if __name__ == '__main__':
    main()
