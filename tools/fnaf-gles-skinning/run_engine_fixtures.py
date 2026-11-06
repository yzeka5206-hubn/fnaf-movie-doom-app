#!/usr/bin/env python3
"""Exercise solid-skin fixture poses through a patched desktop GLES engine.

This requires an X11 display, ImageMagick's import, xdotool, a Freedoom IWAD,
and pre-generated --solid-skin fixtures with nodes. Default mode compares two
fixed poses; --live captures a loop twice within one running engine process. It
does not test an APK, phone, original game logic, every animation or anatomy.

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
from verify_fixture_pose import compare_pose


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


def capture(engine, iwad, fixture, output, group, frame, timeout, live_interval=None):
    result = output / f'{group}-{frame}'
    result.mkdir(parents=True, exist_ok=True)
    log = result / 'engine.log'
    stdout = result / 'stdout.log'
    screenshot = result / 'frame.png'
    later = result / 'frame-later.png'
    config = result / 'config.ini'
    log.unlink(missing_ok=True)
    screenshot.unlink(missing_ok=True)
    later.unlink(missing_ok=True)
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
                if any(s.lower() in text.lower() for s in ('VM execution aborted', 'Could not find animation', 'Script error', 'Fatal error', 'has not been exported from the executable')):
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
                            # MAP01 can be logged while the startup splash is still
                            # on screen; its cyan progress bar alone is not an actor.
                            if im.size == (640, 480) and min(actor_areas) >= 20:
                                if live_interval is None:
                                    return screenshot
                                # Same process and window: this proves the loop
                                # progresses, not just that two restarts differ.
                                time.sleep(live_interval)
                                if proc.poll() is not None:
                                    raise RuntimeError('engine exited between live captures')
                                later_run = subprocess.run(['import', '-window', windows[-1], str(later)],
                                                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                                if later_run.returncode != 0 or not later.exists():
                                    raise RuntimeError('could not capture the second live frame')
                                if Image.open(later).size != (640, 480):
                                    raise RuntimeError('second live frame has the wrong dimensions')
                                return screenshot, later
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
    parser.add_argument('--frames', type=int, nargs=2, default=(0, 15), metavar=('FIRST', 'SECOND'), help='fixed clip-relative frames (default 0 and 15)')
    parser.add_argument('--zoom-arm', action='store_true', help='fixtures were created with --zoom-arm')
    parser.add_argument('--live', action='store_true', help='compare two captures in the same looping engine process (fixtures named GROUP-live)')
    parser.add_argument('--game', type=lambda p: Path(p).resolve(), help='original game PK3 for independent fixed-pose CPU comparison')
    args = parser.parse_args()
    if args.live and args.game:
        parser.error('--game CPU matching is for fixed-pose mode, not --live')
    if any(frame < 0 for frame in args.frames) or args.frames[0] == args.frames[1]:
        parser.error('--frames must be two different nonnegative indices')
    args.output.mkdir(parents=True, exist_ok=True)
    all_results = {}
    for group in args.groups:
        try:
            if args.live:
                frames = capture(args.engine, args.iwad, args.fixtures / f'{group}-live',
                                 args.output, group, 'live', args.timeout, live_interval=0.65)
            else:
                frames = [capture(args.engine, args.iwad, args.fixtures / f'{group}-{frame}',
                                  args.output, group, frame, args.timeout) for frame in args.frames]
            result = {'motion': compare(*frames)}
            if args.game:
                for frame, screenshot in zip(args.frames, frames):
                    result[f'cpu_frame_{frame}'] = compare_pose(args.game, group, frame, screenshot, zoom_arm=args.zoom_arm)
            all_results[group] = result
            good = all(info['pass'] for section in result.values() for info in section.values())
            print(f'{group}: {"PASS" if good else "FAIL"} {json.dumps(result)}', flush=True)
        except Exception as exc:
            all_results[group] = {'error': str(exc)}
            print(f'{group}: ERROR {exc}', flush=True)
        (args.output / 'summary.json').write_text(json.dumps(all_results, indent=2))
    if any('error' in v or not all(i['pass'] for section in v.values() for i in section.values())
           for v in all_results.values()):
        raise SystemExit('One or more fixture comparisons failed')
    mode = 'within a live looping animation' if args.live else 'between fixed animation poses'
    print(f'PASS: selected three-actor fixtures changed {mode}' +
          (' and matched CPU poses' if args.game else ''))


if __name__ == '__main__':
    main()
