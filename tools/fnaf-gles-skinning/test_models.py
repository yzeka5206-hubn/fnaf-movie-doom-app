#!/usr/bin/env python3
"""Compare GPU skinning with independent IQM vertex baking, real game assets.

Usage: python3 tools/fnaf-gles-skinning/test_models.py GAME.pk3 [players|animatronics|other]
Each named clip's first, midpoint and last frame is sampled. This is a headless GLES3
math/asset test, not a complete UZDoom or Xiaomi gameplay test.
"""
from pathlib import Path
import importlib.util, struct, sys, time
from zipfile import ZipFile
import numpy as np

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('gpu_fixture',HERE/'gpu_fixture.py');gpu=importlib.util.module_from_spec(spec);spec.loader.exec_module(gpu)
ROOT=HERE.parents[1]
spec=importlib.util.spec_from_file_location('baker',ROOT/'tools/fnaf-model-fallback/build.py');baker=importlib.util.module_from_spec(spec);spec.loader.exec_module(baker)
C=gpu.C; U=gpu.U

PLAYER_NAMES={
  'Mike':('Mike.iqm','MikeFP.iqm','MikeArm.iqm'),
  'Vanessa':('VanessaMovie.iqm','VanessaMovieFP.iqm','VanessaMovieArm.iqm'),
  'Abby':('Abby.iqm','AbbyFP.iqm','AbbyArm.iqm'),
  'Nick':('Nick.iqm','NickFP.iqm','NickArm.iqm'),
  'Bill':('Bill.iqm','BillFP.iqm','BillArm.iqm'),
  'Alan':('Alan.iqm','AlanFP.iqm','AlanArm.iqm'),
  'Steve':('Steve.iqm','SteveFP.iqm','SteveArm.iqm'),
  'Dustin':('Dustin.iqm','DustinFP.iqm','DustinArm.iqm'),
}
ANIMATRONIC_NAMES=[
 'Characters/Animatronics/Freddy/FreddyMovie.iqm',
 'Characters/Animatronics/Freddy/FreddyMovieW.iqm',
 'Characters/Animatronics/Bonnie/BonnieMovie.iqm',
 'Characters/Animatronics/Chica/ChicaMovie.iqm',
 'Characters/Animatronics/Foxy/FoxyMovie.iqm',
 'Characters/Animatronics/SpringBonnie/SpringBonnieMovieTest.iqm',
]

def header(data): return dict(zip(baker.FIELDS,struct.unpack_from('<27I',data,16)))
def animations(data,h):
    text=data[h['ofs_text']:h['ofs_text']+h['num_text']]
    out=[]
    for i in range(h['num_anims']):
        no,first,count,fps,flags=struct.unpack_from('<IIIfI',data,h['ofs_anims']+i*20)
        end=text.find(b'\0',no); name=text[no:end].decode(errors='replace')
        if first+count>h['num_frames'] or count==0: raise ValueError(f'Invalid clip {name}')
        out.append((name,first,count,fps,flags))
    return out

def arrays(data,h):
    out={}
    for i in range(h['num_vertexarrays']):
        kind,flags,fmt,dim,off=struct.unpack_from('<5I',data,h['ofs_vertexarrays']+20*i)
        if kind in (0,2,4,5):
            expected=(7,3) if kind in (0,2) else (1,4)
            if (fmt,dim)!=expected:raise ValueError('unexpected vertex format')
            out[kind]=np.frombuffer(data,dtype='<f4' if fmt==7 else 'u1',count=h['num_vertexes']*dim,offset=off).reshape((-1,dim))
    return out

def binding(data,h):
    joints=[]
    for i in range(h['num_joints']):
        v=struct.unpack_from('<Ii10f',data,h['ofs_joints']+48*i); parent=v[1]
        local=baker.matrix(np.array(v[2:5]),np.array(v[5:9]),np.array(v[9:12]))
        joints.append((parent,joints[parent][1]@local if parent>=0 else local))
    return joints,np.linalg.inv(np.stack([j[1] for j in joints]))

def palette(data,h,joints,inv,frame):
    at=h['ofs_frames']+frame*h['num_framechannels']*2; end=at+h['num_framechannels']*2; poses=[]
    for i in range(h['num_poses']):
        v=struct.unpack_from('<iI20f',data,h['ofs_poses']+88*i);parent,mask=v[:2]
        if parent!=joints[i][0]:raise ValueError('joint/pose hierarchy mismatch')
        vals=np.array(v[2:12]); scale=np.array(v[12:22])
        for k in range(10):
            if mask&(1<<k):vals[k]+=struct.unpack_from('<H',data,at)[0]*scale[k];at+=2
        local=baker.matrix(vals[:3],vals[3:7],vals[7:]);poses.append(poses[parent]@local if parent>=0 else local)
    if at!=end:raise ValueError('frame channel mismatch')
    t=np.stack(poses)@inv
    # The engine swaps Y and Z both for vertices and bone matrices.
    swap=np.eye(4);swap[1,1]=swap[2,2]=0;swap[1,2]=swap[2,1]=1
    return swap@t@swap

class Runner:
    def __init__(self):
        self.g=gpu.GLES();g=self.g
        fragment='#version 100\nprecision highp float;\nvarying vec4 pixelpos;\nvoid main(){gl_FragColor=vec4(pixelpos.xyz,1.0);}'
        self.programs=[g.program(gpu.shader_source(output_normals=normals),fragment) for normals in (False,True)]
        for program in self.programs:
            g.glUseProgram(program)
            for name in ('ModelMatrix','NormalModelMatrix','ViewMatrix','NormalViewMatrix','ProjectionMatrix','TextureMatrix'):g.matrix_identity(program,name)
            if g.uniform_i(program,'uFnafBoneTexture',17)<0 or g.uniform_i(program,'uBoneIndexBase',0)<0:raise ValueError('palette shader uniforms absent')
        self.palette_tex=g.texture(17,4,256)
        color=g.texture(0,64,64);fb=U();g.glGenFramebuffers(1,C.byref(fb));g.glBindFramebuffer(0x8D40,fb.value)
        g.glFramebufferTexture2D(0x8D40,0x8CE0,0x0DE1,color,0)
        if g.glCheckFramebufferStatus(0x8D40)!=0x8CD5:raise RuntimeError('float framebuffer incomplete')
        g.glViewport(0,0,64,64);g.glDisable(0x0B71);g.glDisable(0x0BE2)
        g.glDisableVertexAttribArray(4);g.glVertexAttrib3f(4,0,0,1);g.glDisableVertexAttribArray(5);g.glVertexAttrib3f(5,0,0,1)

    def draw(self,coords,inds,weights,mats,normals=None):
        g=self.g;n=len(coords); assert n<=4096
        g.glUseProgram(self.programs[normals is not None])
        # Four RGBA texels = four contiguous column vectors of each mat4.
        cols=np.ascontiguousarray(mats.transpose(0,2,1),dtype=np.float32)
        g.glActiveTexture(0x84C0+17);g.glBindTexture(0x0DE1,self.palette_tex)
        g.glTexSubImage2D(0x0DE1,0,0,0,4,len(cols),0x1908,0x1406,gpu.ptr(cols))
        i=np.arange(n);xy=np.column_stack(((i%64+0.5)/64*2-1,(i//64+0.5)/64*2-1)).astype(np.float32)
        buffers=[g.buffer(0,np.ascontiguousarray(coords,np.float32),3,0x1406),g.buffer(7,np.ascontiguousarray(weights,np.uint8),4,0x1401,True),
                 g.buffer(8,np.ascontiguousarray(inds,np.uint8),4,0x1401),g.buffer(9,xy,2,0x1406)]
        if normals is not None:
            # Match FModelVertex::SetNormal and the renderer's signed
            # GL_INT_2_10_10_10_REV normalized attribute, rather than GL_FLOAT.
            q=np.clip(np.trunc(normals*512),-512,511).astype(np.int32)
            packed=((q[:,0]&1023)|((q[:,1]&1023)<<10)|((q[:,2]&1023)<<20)).astype(np.uint32)
            buffers.append(g.buffer(4,np.ascontiguousarray(packed),4,0x8D9F,True))
        else:g.glDisableVertexAttribArray(4);g.glVertexAttrib3f(4,0,0,1)
        g.glClearColor(0,0,0,0);g.glClear(0x4000);g.glDrawArrays(0,0,n);g.glFinish();g.check('rendering skinned vertices')
        pixels=np.empty((64,64,4),np.float32);g.glReadPixels(0,0,64,64,0x1908,0x1406,gpu.ptr(pixels));g.check('reading skinned vertices')
        for b in buffers:g.glDeleteBuffers(1,C.byref(U(b)))
        return pixels.reshape((-1,4))[:n,:3]


def test_one(z,name,runner,exhaustive=False):
    source=z.read(name);h=header(source);a=arrays(source,h);js,inv=binding(source,h); clips=animations(source,h)
    if h['num_joints']>256:raise ValueError(f'{name}: exceeds 256 byte-indexable bones')
    if not clips:raise ValueError(f'{name}: no animation clips')
    frames=[]
    # Each named clip is checked at first, middle and last frames, sampling 512 vertices.
    for clip,start,count,rate,flags in clips:
        for f in (start,start+(count-1)//2,start+count-1):
            if f not in frames:frames.append(f)
    if not exhaustive:
        # Fast check still exercises each model at idle and motion, where present.
        frames=[]
        chosen=[c for c in clips if c[0].lower() in {'idle','walk','run','walkback','crouchwalk'}]
        chosen=chosen[:3] or clips[:2]
        for clip,start,count,rate,flags in chosen:
            for f in (start,start+(count-1)//2):
                if f not in frames:frames.append(f)
    count=min(512,h['num_vertexes']);selected=np.linspace(0,h['num_vertexes']-1,count,dtype=np.int64)
    # Force inclusion of all-zero-weight vertices where present.
    zeros=np.flatnonzero(a[5].sum(axis=1)==0)
    if len(zeros):selected[0]=zeros[0]
    zero_normals=np.flatnonzero(np.linalg.norm(a[2],axis=1)<1e-12)
    if len(zero_normals) and len(selected)>1:selected[1]=zero_normals[0]
    coords=np.array(a[0][selected][:,[0,2,1]],dtype=np.float32)
    indices=a[4][selected];weights=a[5][selected]
    normals=np.array(a[2][selected][:,[0,2,1]],dtype=np.float32)
    worst=0.0; worstnormal=0.0
    for f in frames:
        transforms=palette(source,h,js,inv,f)
        # The earlier baker also validates normals. Some shipped assets (for
        # example BonnieMovie) contain a zero normal; that must not prevent a
        # position comparison.  Fall back only for that identified condition.
        try:
            baked,_=baker.bake_model(source,f,verify_bounds=False)
            expected=arrays(baked,h)[0][selected][:,[0,2,1]]
        except ValueError as exc:
            if str(exc) != "Invalid baked coordinates or normals" or not np.any(np.linalg.norm(a[2],axis=1)<1e-12):
                raise
            w=weights.astype(np.float64); sums=w.sum(axis=1); w/=np.where(sums>0,sums,1)[:,None]
            points=np.column_stack((coords,np.ones(len(coords))))
            expected=np.zeros((len(coords),4),dtype=np.float64)
            for k in range(4):
                safe=np.where(w[:,k]>0,indices[:,k],0)
                expected+=np.einsum('nij,nj->ni',transforms[safe],points)*w[:,k,None]
            expected[sums==0]=points[sums==0]
            expected=expected[:,:3]
            if not np.all(np.isfinite(expected)):raise ValueError('non-finite CPU reference')
        actual=runner.draw(coords,indices,weights,transforms)
        delta=float(np.max(np.abs(actual-expected)));worst=max(worst,delta)
        if not np.isfinite(delta) or delta>0.015:
            i=int(np.unravel_index(np.argmax(np.abs(actual-expected)),actual.shape)[0])
            raise AssertionError(f'{name} frame {f}, vertex {selected[i]}: GPU {actual[i]} != CPU {expected[i]}, diff {delta}')
        # Weighted directions must be unaffected by translations.
        w=weights.astype(np.float64); sums=w.sum(axis=1); w/=np.where(sums>0,sums,1)[:,None]
        q=np.clip(np.trunc(normals*512),-512,511)
        unpacked=np.maximum(q.astype(np.float64)/511,-1.0)
        expectednormal=np.zeros((len(coords),3),dtype=np.float64)
        for k in range(4):
            safe=np.where(w[:,k]>0,indices[:,k],0)
            expectednormal+=np.einsum('nij,nj->ni',transforms[safe,:3,:3],unpacked)*w[:,k,None]
        expectednormal[sums==0]=unpacked[sums==0]
        expectednormal[np.einsum('ni,ni->n',expectednormal,expectednormal)<1e-20]=(0,0,1)
        actualnormal=runner.draw(coords,indices,weights,transforms,normals=normals)
        ndelta=float(np.max(np.abs(actualnormal-expectednormal)));worstnormal=max(worstnormal,ndelta)
        if not np.isfinite(ndelta) or ndelta>0.001:
            i=int(np.unravel_index(np.argmax(np.abs(actualnormal-expectednormal)),actualnormal.shape)[0])
            raise AssertionError(f'{name} frame {f}, normal {selected[i]}: GPU {actualnormal[i]} != CPU {expectednormal[i]}, diff {ndelta}')
    print(f'PASS {name}: joints={h["num_joints"]}, clips={len(clips)}, frames={len(frames)}, vertices/frame={count}, maxerr={worst:.6f}, normalerr={worstnormal:.6f}',flush=True)
    return len(clips),len(frames)


def main():
    if len(sys.argv)<3:raise SystemExit(__doc__)
    group=sys.argv[2]; exhaustive='--all-clips' in sys.argv[3:]
    if group=='players':names=[f'Characters/Players/{person}/{f}' for person,models in PLAYER_NAMES.items() for f in models]
    elif group in ('animatronics','other'):names=None # Discover models from the original archive.
    else:raise SystemExit('group must be players, animatronics, or other')
    start=time.monotonic();r=Runner();total=[0,0]
    with ZipFile(sys.argv[1]) as z:
        if names is None:
            def belongs(n):
                low=n.lower()
                if not low.endswith('.iqm'):return False
                if group=='animatronics':return low.startswith('characters/animatronics/')
                primary={f'characters/players/{person}/{f}'.lower() for person,models in PLAYER_NAMES.items() for f in models}
                return not low.startswith('characters/animatronics/') and low not in primary and header(z.read(n))['num_anims']>0
            names=sorted(n for n in z.namelist() if belongs(n))
        for name in names:
            c,f=test_one(z,name,r,exhaustive);total[0]+=c;total[1]+=f
    print(f'PASS {group}: {len(names)} models, {total[0]} clips, {total[1]} sampled frames, {time.monotonic()-start:.1f}s')

if __name__=='__main__':main()
