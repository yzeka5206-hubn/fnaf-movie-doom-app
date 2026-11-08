#!/usr/bin/env python3
"""Build a smaller forward-only PCH2 delta from two APKs.

Uses bsdiff4 only at build time to locate identical runs inside changed ZIP
payloads. The device receives the same simple SHA-verified PCH2 stream as the
original updater; no bsdiff runtime or network access is needed.

Usage: make-apk-repair-delta-refined.py OLD.apk NEW.apk PATCH.dat
Requires `pip install bsdiff4`. Always replay and compare the resulting patch.
"""
from pathlib import Path
import hashlib
import io
import re
import struct
import sys
import zipfile

import bsdiff4

MIN_RUN = 256
MAX_REFINED_PAYLOAD = 40 * 1024 * 1024
MAX_OPERATIONS = 16384
ZERO_RUN = re.compile(b'\x00{%d,}' % MIN_RUN)


def sha_file(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1 << 20), b''):
            h.update(block)
    return h.digest()


def payload_offset(z, entry):
    with open(z.filename, 'rb') as f:
        f.seek(entry.header_offset)
        header = f.read(30)
    if len(header) != 30 or header[:4] != b'PK\x03\x04':
        raise ValueError('invalid ZIP local header')
    name, extra = struct.unpack_from('<HH', header, 26)
    return entry.header_offset + 30 + name + extra


def equal_range(a, ao, b, bo, length):
    a.seek(ao)
    b.seek(bo)
    while length:
        n = min(length, 1 << 20)
        x, y = a.read(n), b.read(n)
        if len(x) != n or x != y:
            return False
        length -= n
    return True


def decode_bsdiff_i64(encoded):
    # BSDIFF uses little-endian magnitude with the high bit as a sign bit.
    value = int.from_bytes(encoded, 'little')
    magnitude = value & ((1 << 63) - 1)
    return -magnitude if value & (1 << 63) else magnitude


def refined_matches(old_data, new_data, old_base, new_base, earliest):
    """Yield exact, forward-only equal-byte runs in target/source order."""
    control, differences, extra = bsdiff4.core.diff(old_data, new_data)
    # bsdiff4 1.2.x returns triples as a Python list; some implementations
    # expose the serialized BSDIFF control bytes instead. Accept both, and
    # still validate every resulting exact-copy span before writing the PCH2.
    if isinstance(control, (bytes, bytearray)):
        if len(control) % 24:
            raise ValueError('invalid bsdiff control block')
        triples = [tuple(decode_bsdiff_i64(control[ci+i:ci+i+8]) for i in (0, 8, 16))
                   for ci in range(0, len(control), 24)]
    elif isinstance(control, (list, tuple)):
        if any(not isinstance(t, (list, tuple)) or len(t) != 3 or
               any(not isinstance(n, int) for n in t) for t in control):
            raise ValueError('invalid bsdiff control triples')
        triples = control
    else:
        raise ValueError('unsupported bsdiff control representation')
    old_at = new_at = diff_at = extra_at = 0
    for x, y, adjust in triples:
        if x < 0 or y < 0 or new_at + x + y > len(new_data):
            raise ValueError('invalid bsdiff span')
        block = differences[diff_at:diff_at+x]
        if len(block) != x:
            raise ValueError('truncated bsdiff data')
        for run in ZERO_RUN.finditer(block):
            src = old_at + run.start()
            dst = new_at + run.start()
            length = run.end() - run.start()
            absolute = old_base + src
            if src < 0 or src + length > len(old_data) or absolute < earliest:
                continue
            if memoryview(old_data)[src:src+length] != memoryview(new_data)[dst:dst+length]:
                raise ValueError('bsdiff zero run is not byte-identical')
            yield new_base + dst, absolute, length
            earliest = absolute + length
        old_at += x + adjust
        new_at += x + y
        diff_at += x
        extra_at += y
    if new_at != len(new_data) or diff_at != len(differences) or extra_at != len(extra):
        raise ValueError('bsdiff lengths inconsistent')


def plan_operations(selected, target_size, max_operations=MAX_OPERATIONS):
    """Build exact forward-copy/literal operations, safely limiting count.

    If a very different native library yields more copy runs than the Android
    reader allows, retaining the longest runs and encoding omitted bytes as
    literals keeps the APK exact instead of failing or exceeding the limit.
    """
    if target_size < 0 or max_operations < 1:
        raise ValueError('invalid target size or operation limit')
    def make_ops(runs):
        ops = []
        pos = old_end = 0
        for np, op, n in runs:
            if n <= 0 or np < pos or op < old_end or np + n > target_size:
                raise ValueError('invalid, backward or overlapping copy run')
            if np > pos:
                ops.append(('L', pos, np-pos))
            ops.append(('C', op, n))
            pos, old_end = np+n, op+n
        if pos < target_size:
            ops.append(('L', pos, target_size-pos))
        return ops
    ops = make_ops(selected)
    if len(ops) > max_operations:
        max_copies = (max_operations - 1) // 2
        kept = sorted(sorted(selected, key=lambda item: item[2], reverse=True)[:max_copies], key=lambda item: item[0])
        print('Limiting patch copy runs:', len(selected), '->', len(kept),
              'to stay within', max_operations, 'operations', flush=True)
        ops = make_ops(kept)
    if len(ops) > max_operations:
        raise ValueError('could not limit patch operation count')
    return ops


def build(old_path, new_path, patch_path):
    selected = []
    earliest = 0
    refined_count = 0
    with zipfile.ZipFile(old_path) as oz, zipfile.ZipFile(new_path) as nz, \
            old_path.open('rb') as old, new_path.open('rb') as new:
        oldmap = {x.filename: x for x in oz.infolist()}
        for n in sorted(nz.infolist(), key=lambda item: item.header_offset):
            o = oldmap.get(n.filename)
            if o is None or not n.compress_size or not o.compress_size:
                continue
            op, np = payload_offset(oz, o), payload_offset(nz, n)
            if op < earliest:
                continue
            same = (o.CRC, o.compress_type, o.compress_size, o.file_size) == \
                   (n.CRC, n.compress_type, n.compress_size, n.file_size)
            if same and equal_range(old, op, new, np, n.compress_size):
                selected.append((np, op, n.compress_size))
                earliest = op + n.compress_size
            elif 1024 <= n.compress_size <= MAX_REFINED_PAYLOAD and o.compress_size <= MAX_REFINED_PAYLOAD:
                old.seek(op); old_data = old.read(o.compress_size)
                new.seek(np); new_data = new.read(n.compress_size)
                matches = list(refined_matches(old_data, new_data, op, np, earliest))
                if matches:
                    selected.extend(matches)
                    earliest = matches[-1][1] + matches[-1][2]
                    refined_count += 1
                    print('Refined', n.filename, 'runs', len(matches),
                          'copied', sum(x[2] for x in matches), '/', n.compress_size, flush=True)
        ops = plan_operations(selected, new_path.stat().st_size)
        with patch_path.open('wb') as out:
            out.write(b'FNAFPCH2')
            out.write(struct.pack('>QQ', old_path.stat().st_size, new_path.stat().st_size))
            out.write(sha_file(old_path)); out.write(sha_file(new_path))
            out.write(struct.pack('>I', len(ops)))
            for kind, offset, length in ops:
                if kind == 'C':
                    out.write(b'\x00' + struct.pack('>QQ', offset, length))
                else:
                    out.write(b'\x01' + struct.pack('>Q', length))
                    new.seek(offset)
                    remaining = length
                    while remaining:
                        block = new.read(min(1 << 20, remaining))
                        if not block:
                            raise ValueError('short new APK')
                        out.write(block)
                        remaining -= len(block)
    print('Patch bytes:', patch_path.stat().st_size, 'operations:', len(ops),
          'refined entries:', refined_count, 'literal:', sum(o[2] for o in ops if o[0] == 'L'),
          'old SHA:', sha_file(old_path).hex(), 'new SHA:', sha_file(new_path).hex(), flush=True)


if __name__ == '__main__':
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    build(*(Path(a) for a in sys.argv[1:]))
