#!/usr/bin/env python3
"""Build small, disposable UZDoom integration fixtures from original game assets.

Each player's world/first-person/arm models or each animatronic's world model is
spawned in a lit room with a named Walk animation. This invokes UZDoom's actual
IQM loader, animation system, shared bone buffer, GLES renderer and shader.
Requires an independently installed IWAD; does not copy assets into this repo.
"""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from io import BytesIO
import struct, sys
from PIL import Image
from test_models import PLAYER_NAMES

GROUPS={n:[(f'Characters/Players/{n}/{model}','Walk',1.2 if i<2 else 1.0) for i,model in enumerate(models)]
        for n,models in PLAYER_NAMES.items()}
GROUPS.update({n:[(f'Characters/Animatronics/{n}/{model}','Walk',1.0)]*3 for n,model in {
    'Freddy':'FreddyMovie.iqm','Bonnie':'BonnieMovie.iqm','Chica':'ChicaMovie.iqm',
    'Foxy':'FoxyMovie.iqm','SpringBonnie':'SpringBonnieMovieTest.iqm'}.items()})


def wad(lumps):
    offset=12;data=b'';entries=[]
    for name,content in lumps:
        content=content.encode() if isinstance(content,str) else content
        entries.append(struct.pack('<II8s',offset,len(content),name.encode().ljust(8,b'\0')))
        offset+=len(content);data+=content
    return struct.pack('<4sII',b'PWAD',len(lumps),offset)+data+b''.join(entries)


def map_data():
    text='namespace = "ZDoom";\n'
    for x,y in [(-512,-512),(-512,512),(512,512),(512,-512)]:text+=f'vertex {{ x={x}; y={y}; }}\n'
    for v in range(4):
        text+=f'linedef {{ v1={v}; v2={(v+1)%4}; sidefront={v}; blocking=true; }}\n'
        text+='sidedef { sector=0; texturemiddle="STARTAN3"; }\n'
    text+='sector { heightfloor=0; heightceiling=256; texturefloor="FLOOR0_1"; textureceiling="CEIL1_1"; lightlevel=255; }\n'
    text+='thing { x=-220; y=0; type=1; angle=0; skill1=true; skill2=true; skill3=true; skill4=true; skill5=true; single=true; }\n'
    for i,y in enumerate((-105,0,105)):
        text+=f'thing {{ x=70; y={y}; type={30000+i}; angle=180; skill1=true; skill2=true; skill3=true; skill4=true; skill5=true; single=true; }}\n'
    return wad([('MAP01',b''),('TEXTMAP',text),('ENDMAP',b'')])


def main():
    if len(sys.argv)!=4 or sys.argv[2] not in GROUPS:
        raise SystemExit('Usage: make_engine_fixture.py game.pk3 '+str(list(GROUPS))+' output-directory')
    game,group,out=sys.argv[1:];out=Path(out);out.mkdir(parents=True,exist_ok=True)
    (out/'fixture.wad').write_bytes(map_data())
    png=BytesIO();Image.new('RGBA',(16,16),(128,0,200,255)).save(png,format='PNG')
    classes='version "4.12.0"\n';modeldefs='';ednums='DoomEdNums\n{\n';members=set()
    with ZipFile(game) as src, ZipFile(out/'fixture.pk3','w',ZIP_DEFLATED,compresslevel=6) as dst:
        for i,(model,clip,scale) in enumerate(GROUPS[group]):
            cls=f'SkeletalFixture{i}';folder,filename=model.rsplit('/',1)
            classes+=f'''class {cls} : Actor
{{
 Default {{ Radius 8; Height 72; +NOGRAVITY; }}
 States {{ Spawn:
   TSTX A 0 SetAnimation('{clip}', framerate: 24, flags: SAF_LOOP);
   TSTX A 1;
   Loop;
 }}
}}
'''
            modeldefs+=f'''Model {cls}
{{
 Path "{folder}";
 Model 0 "{filename}";
 Scale {scale} {scale} {scale};
 AngleOffset 90;
 BaseFrame;
}}
'''.replace(';\n','\n') # MODELDEF does not use semicolons
            ednums+=f' {30000+i} = {cls}\n'
            for n in src.namelist():
                if n==model or (n.startswith(folder+'/') and n.lower().endswith(('.png','.jpg','.jpeg'))):members.add(n)
        ednums+='}\n'
        dst.writestr('ZSCRIPT',classes);dst.writestr('MODELDEF',modeldefs)
        dst.writestr('MAPINFO',ednums);dst.writestr('sprites/TSTXA0.png',png.getvalue())
        for n in sorted(members):dst.writestr(n,src.read(n))
    print(f'Built {group} fixture: {len(GROUPS[group])} actors, {len(members)} asset files, {out}')

if __name__=='__main__':main()
