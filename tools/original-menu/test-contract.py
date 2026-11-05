#!/usr/bin/env python3
"""Native launcher host checks; these do NOT replace testing on Android."""
from pathlib import Path
import hashlib
import io
import re
import shutil
import struct
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT/'doom/src/main/java'
ASSET = ROOT/'doom/src/main/assets/original-menu/Assets.dat'
SOURCE = ROOT/'tools/original-menu/launcher-source.tar.gz'
profile = JAVA/'com/msa/freedoom/FnafLaunchProfile.java'
controller = (JAVA/'com/msa/freedoom/FnafMenuActivity.kt').read_text()
activity = (JAVA/'com/msa/freedoom/OriginalMenuActivity.kt').read_text()
sdl = (JAVA/'org/libsdl/app2012/SDLActivity.java').read_text()
engine = (JAVA/'org/libsdl/app/SDLOpenTouch.java').read_text()
data = ASSET.read_bytes()
expected_sha = re.search(r'ASSET_SHA256 = "([0-9a-f]{64})"', controller).group(1)
expected_size = int(re.search(r'ASSET_SIZE = ([\d_]+)L', controller).group(1).replace('_', ''))
assert len(data)==expected_size and hashlib.sha256(data).hexdigest()==expected_sha
assert 'override fun useOpenTouch(): Boolean = false' in activity
assert 'if (SDLActivity.OPENTOUCH_SDL_EXTRA && SDLActivity.mSingleton != null' in sdl
assert 'onNativeMouse(1, MotionEvent.ACTION_DOWN' in sdl
assert 'onNativeMouse(0, MotionEvent.ACTION_UP' in sdl
original_branch = engine.split('if (fromOriginalMenu) {',1)[1].split('} else {',1)[0]
assert 'FnafLaunchProfile.buildArgs' in original_branch and '+map OUTSIDE' not in original_branch
assert 'options |= 0x4;' in engine and 'options |= 0x2;' in engine

with tarfile.open(SOURCE, 'r:gz') as archive:
    source_header = archive.extractfile('launcher-source/assets.h').read().decode()
    generated_events = archive.extractfile('launcher-source/events_3.cpp').read().decode()
    bridge_code = archive.extractfile('launcher-source/launcher_bridge.cpp').read().decode()
    assert 'launcher_request_singleplayer' in generated_events
    assert '"NIGHT5"' in bridge_code and 'GetMethodID' in bridge_code
    assert not any(x.name.lower().endswith(('.ttf','.otf')) for x in archive.getmembers())

counts = [int(re.search(r'#define '+x+r'_COUNT (\d+)',source_header).group(1))
          for x in ['IMAGE','SOUND','FONT','SHADER','FILE']]
images,sounds,fonts,shaders,files=counts
offsets=struct.unpack_from('<'+'I'*sum(counts),data,images*2)
assert list(offsets)==sorted(offsets) and files==1
# The unused raw, separately licensed embedded font record is empty.
assert struct.unpack_from('<I',data,offsets[-1])[0] == 0
shader_start=images+sounds+fonts
with tempfile.TemporaryDirectory() as tmp:
    p=Path(tmp)
    glsl=shutil.which('glslangValidator')
    for i in range(shaders):
        off=offsets[shader_start+i]
        n=struct.unpack_from('<I',data,off)[0];off+=4; vert=data[off:off+n].decode();off+=n
        n=struct.unpack_from('<I',data,off)[0];off+=4; frag=data[off:off+n].decode()
        assert vert.startswith('#version 100\n') and frag.startswith('#version 100\n')
        assert not any(x in vert+frag for x in ['gl_Vertex','gl_FrontColor','gl_MultiTexCoord','\\\n'])
        for shader in (vert,frag):
            assert not re.search(r'^\s*varying\s+(?!mediump\s)',shader,re.M)
        if glsl:
            vp=p/f'{i}.vert';fp=p/f'{i}.frag';vp.write_text(vert);fp.write_text(frag)
            subprocess.run([glsl,'-l',str(vp),str(fp)],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,check=True)
    subprocess.run(['javac','-d',tmp,str(profile),str(ROOT/'tools/original-menu/ProfileTest.java')],check=True)
    subprocess.run(['java','-cp',tmp,'ProfileTest'],check=True)
for abi in ['arm64-v8a','armeabi-v7a']:
    lib=ROOT/f'doom/src/main/libs/{abi}/libChowdren.so'
    h=subprocess.check_output(['readelf','-h',str(lib)],text=True)
    syms=subprocess.check_output(['readelf','-sW',str(lib)],text=True)
    assert ('AArch64' if abi=='arm64-v8a' else 'ARM') in h and ' SDL_main' in syms
    runner=ROOT/f'doom/src/main/libs/{abi}/libFnafMenuRunner.so'
    runner_syms=subprocess.check_output(['readelf','-sW',str(runner)],text=True)
    needed=subprocess.check_output(['readelf','-d',str(runner)],text=True)
    assert 'Java_com_msa_freedoom_OriginalMenuActivity_nativeRunConvertedLauncher' in runner_syms
    assert ' UND SDL_main' in runner_syms and '[libChowdren.so]' in needed
    if abi=='arm64-v8a':
        ph=subprocess.check_output(['readelf','-lW',str(lib)],text=True)
        for row in ph.splitlines():
            if row.strip().startswith('LOAD '): assert row.split()[-1]=='0x4000'
print(f'PASS: asset hash, {shaders} ES shader pairs' + (' validated' if glsl else ' inspected') +
      '; unused raw font removed; SDL/game separation, touch, keyboard options and ARM ELF checks.')
