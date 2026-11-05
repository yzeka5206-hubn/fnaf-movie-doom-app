#!/usr/bin/env python3
"""Patch the engine base PK3's built-in GLES vertex shader reproducibly.

FShader::Load intentionally looks for this shader in resource file 0, so an
ordinary mod PK3 cannot override it. This updates only that member, leaving the
other compressed ZIP streams intact. Do not apply to an unknown engine PK3.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile
import hashlib, os, shutil, subprocess, sys
HERE=Path(__file__).resolve().parent
EXPECTED='ccd977bf3274d1c0bee8e01c226c356b75a8f7aeae8481a2c86b7d48bce4af47'
MEMBER='shaders_gles/glsl/main.vp'

def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def main():
    if len(sys.argv)!=3:raise SystemExit('Usage: patch_resource.py original-uzdoom.pk3 output-uzdoom.pk3')
    original=Path(sys.argv[1]).resolve();target=Path(sys.argv[2]).resolve()
    if original==target:raise SystemExit('Use a separate output file')
    if digest(original)!=EXPECTED:raise SystemExit('Unexpected source uzdoom.pk3 SHA-256; refusing to patch')
    new=(HERE/'main.vp').read_bytes();target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(original,target)
    with TemporaryDirectory() as tmp:
        root=Path(tmp);f=root/MEMBER;f.parent.mkdir(parents=True);f.write_bytes(new)
        os.utime(f,(1781740800,1781740800)) # fixed timestamp; zip -X omits extra metadata
        subprocess.run(['zip','-q','-X',str(target),MEMBER],cwd=root,check=True)
    with ZipFile(original) as a, ZipFile(target) as b:
        if a.testzip() or b.testzip() or len(a.namelist())!=len(b.namelist()):raise RuntimeError('invalid output ZIP')
        if b.namelist().count(MEMBER)!=1 or b.read(MEMBER)!=new:raise RuntimeError('shader replacement failed')
        for n in a.namelist():
            if n!=MEMBER and a.getinfo(n).CRC!=b.getinfo(n).CRC:raise RuntimeError(f'unrelated resource changed: {n}')
    print(f'{target}: {target.stat().st_size} bytes sha256 {digest(target)}')

if __name__=='__main__':main()
