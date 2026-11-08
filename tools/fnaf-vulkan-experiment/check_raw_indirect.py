#!/usr/bin/env python3
"""Fail closed if an engine renderer starts bypassing our indirect wrapper.

The current renderer issues only direct draws. A future raw indirect call must
be audited before the mobile multiDrawIndirect feature is treated as optional.
"""
from pathlib import Path
import re, sys
root = Path(sys.argv[1]) / 'src'
if not root.is_dir():
    raise SystemExit('missing engine src')
raw = re.compile(r'\b(?:vkCmdDraw(?:Indexed)?Indirect(?:Count)?(?:KHR)?|draw(?:Indexed)?Indirect)\s*\(')
violations=[]
for path in root.rglob('*'):
    if path.suffix not in {'.cpp', '.c', '.h', '.hpp'} or not path.is_file():
        continue
    for number, line in enumerate(path.read_text(errors='replace').splitlines(), 1):
        if raw.search(line): violations.append(f'{path}:{number}:{line.strip()}')
if violations:
    print('\n'.join(violations), file=sys.stderr)
    raise SystemExit('FAIL: audit raw indirect renderer usage before continuing')
print('PASS engine renderer does not bypass the audited indirect-draw fallback')
