#!/usr/bin/env python3
"""Conservative cull vs CPU-skinned *actual* game vertices (not an Android test).

Builds cull_probe.cpp, samples each IQM's first/mid/last frame, and tries several
view transforms. Whenever the production math rejects a model, independently
compute every weighted vertex and require the whole model to be outside the
same homogeneous clip plane. User supplies the legally obtained game PK3.
"""
from pathlib import Path
from zipfile import ZipFile
import ctypes as C
import importlib.util
import os
import subprocess
import sys
import tempfile
import numpy as np

HERE = Path(__file__).resolve().parent
TESTS = HERE.parent / 'fnaf-gles-skinning'
sys.path.insert(0, str(TESTS))
from test_models import header, arrays, binding, palette


def main(path):
    with tempfile.TemporaryDirectory() as temp:
        library = Path(temp) / 'cull.so'
        subprocess.run(['g++','-std=c++17','-O2','-shared','-fPIC',
                        str(HERE/'cull_probe.cpp'),'-o',str(library)],check=True)
        lib = C.CDLL(str(library))
        p64 = C.POINTER(C.c_double); p32 = C.POINTER(C.c_float); p8 = C.POINTER(C.c_uint8)
        lib.fnaf_cull_probe.argtypes = [p64,p32,p8,p8,C.c_int,C.c_int,p32]
        lib.fnaf_cull_probe.restype = C.c_int
        tested=rejected=retained=skipped=0
        with ZipFile(path) as z:
            names=[n for n in z.namelist() if n.lower().endswith('.iqm')]
            for name in names:
                data=z.read(name); h=header(data)
                if not h['num_frames'] or h['num_joints'] != h['num_poses'] or not h['num_joints']:
                    skipped += 1; continue
                a=arrays(data,h); vertices=np.ascontiguousarray(a[0][:,[0,2,1]],dtype=np.float32)
                inds=np.ascontiguousarray(a[4],dtype=np.uint8)
                ws=np.ascontiguousarray(a[5],dtype=np.uint8)
                joints,inv=binding(data,h)
                if np.any((inds >= h['num_joints']) & (ws > 0)):
                    raise AssertionError(f'{name}: positive-weight bone index out of range')
                for frame in sorted({0,h['num_frames']//2,h['num_frames']-1}):
                    mat=palette(data,h,joints,inv,frame)
                    pal=np.ascontiguousarray(mat.transpose(0,2,1),dtype=np.float32)
                    weights=ws.astype(np.float64); total=weights.sum(axis=1)
                    weights/=np.where(total>0,total,1)[:,None]
                    homogeneous=np.column_stack((vertices,np.ones(len(vertices))))
                    posed=np.zeros((len(vertices),4),dtype=np.float64)
                    for j in range(4):
                        safe=np.where(weights[:,j]>0,inds[:,j],0)
                        posed += np.einsum('nij,nj->ni',mat[safe],homogeneous)*weights[:,j,None]
                    posed[total==0]=homogeneous[total==0]
                    posed[:,3]=1
                    # Include offsets, rotation and varying W: these matrices
                    # are deliberately not derived from actor collision sizes.
                    for i,(tx,ty) in enumerate([(-4,0),(4,0),(0,-4),(0,4),(0,0),(1,0)]):
                        angle=(i*.37)+frame*.0001
                        m=np.eye(4,dtype=np.float64)
                        m[0,0]=np.cos(angle)*.01; m[0,2]=-np.sin(angle)*.01
                        m[1,1]=.01; m[1,2]=np.sin(angle)*.002
                        m[2,0]=np.sin(angle)*.01; m[2,2]=np.cos(angle)*.01
                        m[0,3]=tx; m[1,3]=ty; m[3,2]=.0002
                        flat=np.ascontiguousarray(m.T.reshape(-1))
                        yes=lib.fnaf_cull_probe(flat.ctypes.data_as(p64),vertices.ctypes.data_as(p32),
                             inds.ctypes.data_as(p8),ws.ctypes.data_as(p8),len(vertices),h['num_joints'],
                             pal.ctypes.data_as(p32))
                        tested += 1
                        if yes:
                            rejected += 1
                            # SIMPLE/unsupported shader paths can draw bind pose.
                            # Both possible outputs must be outside the SAME plane.
                            clip=np.vstack((posed @ m.T, homogeneous @ m.T))
                            plane=np.column_stack((clip[:,3]+clip[:,0],clip[:,3]-clip[:,0],
                                                   clip[:,3]+clip[:,1],clip[:,3]-clip[:,1]))
                            if not np.any(np.all(plane < -1e-8,axis=0)):
                                raise AssertionError(f'false cull: {name} frame {frame} view {i}')
                        else: retained += 1
                print(f'PASS {name}',flush=True)
        if tested < 1000 or rejected < 100 or retained < 100:
            raise AssertionError(f'insufficient coverage {tested=} {rejected=} {retained=}')
        print(f'PASS: {len(names)-skipped} IQM models, {tested} real pose/views, '
              f'{rejected} rejected / {retained} retained, {skipped} no-frame fallback')

if __name__ == '__main__':
    if len(sys.argv)!=2: raise SystemExit('usage: test_real_iqm_cull.py GAME.pk3')
    main(sys.argv[1])
