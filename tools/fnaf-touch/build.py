#!/usr/bin/env python3
"""Create the tiny deterministic addon; original game archives are unchanged."""
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo
here = Path(__file__).resolve().parent
output = here.parent.parent / 'doom/src/main/assets/game/fnaf-touch-v1.pk3'
output.parent.mkdir(parents=True, exist_ok=True)
with ZipFile(output, 'w', compression=ZIP_DEFLATED, compresslevel=9) as z:
    for name in ('ZSCRIPT', 'MAPINFO'):
        info = ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        z.writestr(info, (here / name).read_bytes(), compress_type=ZIP_DEFLATED, compresslevel=9)
print(output, output.stat().st_size)
