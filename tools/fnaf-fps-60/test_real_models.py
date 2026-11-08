#!/usr/bin/env python3
"""Check new culling math against every original OBJ/MD3 model's vertices.

Not a whole-game, GPU, frame-pacing, FPS, or Android device test. Reads the
user's original PK3 (not included here). Uses independent vector math to verify
that each rejection is outside a common homogeneous clip plane; MD3 test boxes
cover all surfaces and all frames, as the engine does. Includes axis conversion.
"""
from pathlib import Path
from zipfile import ZipFile
import ctypes as C
from contextlib import ExitStack
import math
import struct
import subprocess
import sys
import tempfile
import numpy as np

HERE=Path(__file__).resolve().parent

def obj_vertices(data):
    out=[]
    for line in data.splitlines():
        q=line.split()
        if len(q)>=4 and q[0]==b'v':
            try: out.append((float(q[1]),float(q[2]),-float(q[3])))
            except ValueError: return None
    return np.ascontiguousarray(out,dtype=np.float32).reshape(-1,3)

def md3_vertices(data):
    h=struct.unpack_from('<4si64s9I',data)
    assert h[0]==b'IDP3' and h[1]==15
    frames,surfs,offset=h[4],h[6],h[10]
    parts=[]
    for _ in range(surfs):
        fields=struct.unpack_from('<4s64s10I',data,offset)
        assert fields[0]==b'IDP3'
        nv=fields[5]; xyz=fields[10]; end=fields[11]
        # Some originals have empty material surfaces; the engine still walks
        # them and they simply contribute no vertices to the union.
        assert frames>0 and end>0 and offset+xyz+nv*frames*8<=len(data)
        array=np.frombuffer(data,dtype='<i2',count=4*nv*frames,offset=offset+xyz).reshape(-1,4)
        parts.append(array[:,[0,2,1]].astype(np.float32)/np.float32(64))
        offset+=end
    if not parts: return np.empty((0,3),dtype=np.float32)
    return np.ascontiguousarray(np.concatenate(parts),dtype=np.float32)

def main(paths):
    counts={'obj':0,'md3':0,'vertices':0,'tested':0,'rejected':0,'kept':0,'invalid_skipped':0}
    with tempfile.TemporaryDirectory() as tmp:
        library=Path(tmp)/'probe.so'
        subprocess.run(['g++','-std=c++17','-O2','-shared','-fPIC',
            '-I'+str(HERE.parent/'fnaf-gles-optimization'),str(HERE/'static_probe.cpp'),'-o',str(library)],check=True)
        lib=C.CDLL(str(library)); p64=C.POINTER(C.c_double); p32=C.POINTER(C.c_float)
        lib.fnaf_static_probe.argtypes=[p64,p32,C.c_int];lib.fnaf_static_probe.restype=C.c_int
        with ExitStack() as stack:
            archives=[stack.enter_context(ZipFile(path)) for path in paths]
            models=[(z,n) for z in archives for n in z.namelist() if n.lower().endswith(('.obj','.md3'))]
            for z,name in models:
                data=z.read(name); kind=name[-3:].lower()
                try: vertices=obj_vertices(data) if kind=='obj' else md3_vertices(data)
                except (AssertionError,ValueError,struct.error) as e:
                    # An unsupported model is NOT deemed safe; the live engine
                    # must fail-open when it cannot construct a bound.
                    print('SKIP malformed/unsupported',name,str(e),flush=True);counts['invalid_skipped']+=1;continue
                if vertices is None or not len(vertices) or not np.isfinite(vertices).all():
                    print('SKIP empty/non-finite',name,flush=True);counts['invalid_skipped']+=1;continue
                counts[kind]+=1;counts['vertices']+=len(vertices)
                lo=vertices.min(axis=0);hi=vertices.max(axis=0);center=(lo+hi)/2
                scale=1.0/max(float(np.max(hi-lo)),1e-6)
                h=np.column_stack((vertices.astype(np.float64),np.ones(len(vertices))))
                for i,(tx,ty) in enumerate([(-5,0),(5,0),(0,-5),(0,5),(0,0),(1,0)]):
                    a=i*.41; co=math.cos(a);si=math.sin(a);m=np.eye(4,dtype=np.float64)
                    m[0,:3]=np.array([co,0,-si])*scale
                    m[1,:3]=np.array([0,1,.12*si])*scale
                    m[2,:3]=np.array([si,0,co])*scale
                    m[0,3]=tx-m[0,:3]@center;m[1,3]=ty-m[1,:3]@center
                    m[3,:3]=np.array([0,0,.005])*scale;m[3,3]=1-m[3,:3]@center
                    flat=np.ascontiguousarray(m.T.reshape(-1),dtype=np.float64)
                    culled=lib.fnaf_static_probe(flat.ctypes.data_as(p64),vertices.ctypes.data_as(p32),len(vertices))
                    counts['tested']+=1
                    if culled:
                        counts['rejected']+=1;clip=h@m.T
                        planes=np.column_stack((clip[:,3]+clip[:,0],clip[:,3]-clip[:,0],clip[:,3]+clip[:,1],clip[:,3]-clip[:,1]))
                        if not np.any(np.all(planes < -1e-8,axis=0)):
                            raise AssertionError('false rejection: '+name+' view '+str(i))
                    else:counts['kept']+=1
                print('PASS',name,'vertices',len(vertices),flush=True)
    assert counts['obj']>=190 and counts['md3']>=70 and counts['rejected']>100 and counts['kept']>100,counts
    print('PASS original game static models:',counts,flush=True)

if __name__=='__main__':
    if len(sys.argv)<2: raise SystemExit('usage: test_real_models.py GAME.pk3 [FRAME-ADDON.pk3 ...]')
    main(sys.argv[1:])
