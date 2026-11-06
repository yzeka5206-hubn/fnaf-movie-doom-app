#!/usr/bin/env python3
"""Build small, disposable UZDoom integration fixtures from original game assets.

Each player's world/first-person/arm models or three copies of an animatronic
model are spawned with a named animation. This invokes UZDoom's IQM/MD3 loader,
animation system, shared bone buffer, GLES renderer and shader. Requires an
independently installed IWAD; does not copy game assets into this repo.
"""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from io import BytesIO
import argparse, struct, sys
from PIL import Image
from test_models import PLAYER_NAMES, header, animations

GROUPS={n:[(f'Characters/Players/{n}/{model}','Walk',1.2 if i<2 else 1.0) for i,model in enumerate(models)]
        for n,models in PLAYER_NAMES.items()}
GROUPS.update({n:[(f'Characters/Animatronics/{n}/{model}','Walk',1.0)]*3 for n,model in {
    'Freddy':'FreddyMovie.iqm','Bonnie':'BonnieMovie.iqm','Chica':'ChicaMovie.iqm',
    'Foxy':'FoxyMovie.iqm','SpringBonnie':'SpringBonnieMovieTest.iqm'}.items()})
# These use classic FrameIndex/sprite states in the actual game's MODELDEF,
# rather than the player's decoupled SetAnimation/BaseFrame route.
for group, folder, model, clip, scale in [
    ('FreddyJP','Freddy','FreddyMovieJP.iqm','Freddy',1.0),
    ('BonnieJP','Bonnie','BonnieMovieJP.iqm','Bonnie',1.0),
    ('ChicaJS','Chica','ChicaMovieJS.iqm','Chica',1.0),
    ('FoxyJS','Foxy','FoxyMovieJS.iqm','Foxy',1.0),
    ('SpringBonnieJS','SpringBonnie','SpringBonnieJS.iqm','AftonAction',1.0),
    ('Cupcake','Chica','CupcakeMovie.iqm','Cupcake',3.0),
    ('FreddyPlush','Freddy','FreddyPlush.iqm','Plush',3.0),
    ('Pepsi','Pepsiman','PepsiMan.iqm','Armature|mixamo.com|Layer0',45.0),
]:
    GROUPS[group] = [(f'Characters/Animatronics/{folder}/{model}', clip, scale)] * 3
# Uniform diagnostic scales keep three large props inside the small room; they
# are not changes to the actual game's MODELDEF.
GROUPS.update({
    'Curtain1': [('Props/Scenery/Curtain/Palco.iqm', 'Opening', .30)] * 3,
    'Curtain2': [('Props/Scenery/Curtain/palco2.iqm', 'Open', .50)] * 3,
    'CarIntro': [('Props/Scenery/MikeCar/CarScene2.iqm', 'default_sequence_Armature_0', .15)] * 3,
    'DriverIntro': [('Characters/Players/Mike/MikeCarScene2.iqm', 'Cut2', .20)] * 3,
})


def wad(lumps):
    offset=12;data=b'';entries=[]
    for name,content in lumps:
        content=content.encode() if isinstance(content,str) else content
        entries.append(struct.pack('<II8s',offset,len(content),name.encode().ljust(8,b'\0')))
        offset+=len(content);data+=content
    return struct.pack('<4sII',b'PWAD',len(lumps),offset)+data+b''.join(entries)


# The first-person arms can be very small in a full-body fixture. A separate
# diagnostic zoom gives their pose enough pixels to test without changing the
# game itself. Both generator and CPU reference use these exact settings.
ARM_ZOOM_SCALE = 4.0
ARM_ZOOM_HEIGHT = 160


def fixture_models(group, zoom_arm=False):
    if zoom_arm and group not in PLAYER_NAMES:
        raise ValueError('--zoom-arm is only available for player fixtures')
    return [(model, clip, scale * (ARM_ZOOM_SCALE if zoom_arm and i == 2 else 1.0))
            for i, (model, clip, scale) in enumerate(GROUPS[group])]


def map_data(zoom_arm=False):
    text='namespace = "ZDoom";\n'
    for x,y in [(-512,-512),(-512,512),(512,512),(512,-512)]:text+=f'vertex {{ x={x}; y={y}; }}\n'
    for v in range(4):
        text+=f'linedef {{ v1={v}; v2={(v+1)%4}; sidefront={v}; blocking=true; }}\n'
        text+='sidedef { sector=0; texturemiddle="STARTAN3"; }\n'
    text+='sector { heightfloor=0; heightceiling=256; texturefloor="FLOOR0_1"; textureceiling="CEIL1_1"; lightlevel=255; }\n'
    text+='thing { x=-320; y=0; type=1; angle=0; skill1=true; skill2=true; skill3=true; skill4=true; skill5=true; single=true; }\n'
    # Raise the actors above the floor, including arms whose Walk pose is
    # below their local origin. Back the camera up to fit tall animatronics.
    for i,y in enumerate((-125,0,125)):
        height = ARM_ZOOM_HEIGHT if zoom_arm and i == 2 else 64
        text+=f'thing {{ x=70; y={y}; height={height}; type={30000+i}; angle=180; skill1=true; skill2=true; skill3=true; skill4=true; skill5=true; single=true; }}\n'
    return wad([('MAP01',b''),('TEXTMAP',text),('ENDMAP',b'')])


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('game', help='original game PK3')
    parser.add_argument('group', choices=sorted(GROUPS))
    parser.add_argument('out', type=Path)
    parser.add_argument('frame', type=int, nargs='?', help='optional fixed frame relative to the group\'s named clip')
    parser.add_argument('--solid-skin', action='store_true', help='diagnostic colored skins to expose each mesh')
    parser.add_argument('--zoom-arm', action='store_true', help='magnify and raise the third (first-person arm) player model for diagnostic comparisons')
    parser.add_argument('--frame-index', action='store_true', help='drive IQM poses with classic sprite FrameIndex instead of SetAnimation/BaseFrame')
    parser.add_argument('--pepsi-md3', type=Path, help='use Pepsi MD3 from the actual frame addon (requires Pepsi and --frame-index)')
    args=parser.parse_args()
    game,group,out,frame=args.game,args.group,args.out,args.frame
    if args.pepsi_md3 and (group != 'Pepsi' or not args.frame_index):
        parser.error('--pepsi-md3 requires the Pepsi group and --frame-index')
    models=fixture_models(group,args.zoom_arm)
    out.mkdir(parents=True,exist_ok=True)
    if frame is not None and frame < 0: raise ValueError('frame must be nonnegative')
    # Optional frozen poses permit deterministic framebuffer comparisons against
    # a full desktop GL reference; the default is still a real looping animation.
    animation_options = ('framerate: 0, startFrame: '+str(frame)+', flags: SAF_LOOP|SAF_INSTANT'
                         if frame is not None else 'framerate: 24, flags: SAF_LOOP|SAF_NOOVERRIDE')
    (out/'fixture.wad').write_bytes(map_data(args.zoom_arm))
    png=BytesIO();Image.new('RGBA',(16,16),(128,0,200,255)).save(png,format='PNG')
    classes='version "4.12.0"\n';modeldefs='';ednums='DoomEdNums\n{\n';members=set();solid_assets={}
    with ZipFile(game) as src, ZipFile(out/'fixture.pk3','w',ZIP_DEFLATED,compresslevel=6) as dst:
        for i,(model,clip,scale) in enumerate(models):
            # Fail early rather than accepting a test room whose named pose is
            # absent (the engine otherwise prints a warning and shows no motion).
            model_bytes=src.read(model)
            matches=[entry for entry in animations(model_bytes,header(model_bytes)) if entry[0]==clip]
            if len(matches)!=1:
                raise ValueError(f'{model}: expected exactly one {clip!r} animation, found {len(matches)}')
            if frame is not None and frame >= matches[0][2]:
                raise ValueError(f'{model}: {clip} has {matches[0][2]} frames; requested {frame}')
            cls=f'SkeletalFixture{i}';folder,filename=model.rsplit('/',1)
            render_scale = scale
            if args.pepsi_md3:
                filename = 'PepsiMan-fnaf-frames.md3'
                # The addon bakes coordinates 128 times larger than the IQM.
                render_scale = scale / 128.0
            if args.frame_index:
                # Real jumpscare and prop classes advance sprite frames and map
                # them directly to absolute IQM frame numbers in MODELDEF.
                letters = 'A' if frame is not None else 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'[:min(matches[0][2], 26)]
                animation_state = f'TSTX {letters} 1;'
                model_frames = '\n'.join(f' FrameIndex TSTX {letter} 0 {matches[0][1] + (frame if frame is not None else pos)}'
                                         for pos, letter in enumerate(letters))
                decoupled = ''
            else:
                animation_state = f"TSTX A 0 SetAnimation('{clip}', {animation_options});\n   TSTX A 1;"
                model_frames = ' BaseFrame'
                decoupled = '+DECOUPLEDANIMATIONS;'
            classes+=f'''class {cls} : Actor
{{
 Default {{ Radius 8; Height 72; +NOGRAVITY; {decoupled} }}
 States {{ Spawn:
   {animation_state}
   Loop;
 }}
}}
'''
            solid_name=f'fixture-solid-{i}.png'
            skin_line=f' Skin 0 "{solid_name}";\n' if args.solid_skin else ''
            # Three separate colors expose per-actor deformation and also make
            # swapped palettes or missing mesh parts obvious in screenshots.
            if args.solid_skin: solid_assets[folder+'/'+solid_name]=[(0,220,255,255),(25,245,25,255),(255,0,220,255)][i]
            modeldefs+=f'''Model {cls}
{{
 Path "{folder}";
 Model 0 "{filename}";
{skin_line} Scale {render_scale} {render_scale} {render_scale};
 AngleOffset 90;
{model_frames}
}}
'''.replace(';\n','\n') # MODELDEF does not use semicolons
            ednums+=f' {30000+i} = {cls}\n'
            for n in src.namelist():
                if n==model or (n.startswith(folder+'/') and n.lower().endswith(('.png','.jpg','.jpeg'))):members.add(n)
        ednums+='}\n'
        # BaseFrame-only fixture used to leave UZDoom's sprite-model hash empty,
        # causing division by zero before animation. Seed it with a never-spawned
        # dummy frame; the three tested actors retain BaseFrame/SetAnimation.
        classes+='class SkeletalFixtureHashSeed : Actor { States { Spawn: TSTX A -1; Stop; } }\n'
        seed_folder,seed_filename=GROUPS[group][0][0].rsplit('/',1)
        modeldefs+=f'Model SkeletalFixtureHashSeed {{ Path "{seed_folder}" Model 0 "{seed_filename}" FrameIndex TSTX A 0 0 }}\n'
        dst.writestr('ZSCRIPT',classes);dst.writestr('MODELDEF',modeldefs)
        dst.writestr('MAPINFO',ednums)
        for letter in ('ABCDEFGHIJKLMNOPQRSTUVWXYZ' if args.frame_index and frame is None else 'A'):
            dst.writestr(f'sprites/TSTX{letter}0.png',png.getvalue())
        for n in sorted(members):dst.writestr(n,src.read(n))
        if args.pepsi_md3:
            md3name = 'Characters/Animatronics/Pepsiman/PepsiMan-fnaf-frames.md3'
            with ZipFile(args.pepsi_md3) as addon:
                dst.writestr(md3name, addon.read(md3name))
        for name,color in sorted(solid_assets.items()):
            solid=BytesIO();Image.new('RGBA',(8,8),color).save(solid,format='PNG')
            dst.writestr(name,solid.getvalue())
    print(f'Built {group} fixture: {len(GROUPS[group])} actors, {len(members)} asset files, {out}')

if __name__=='__main__':main()
