#!/usr/bin/env python3
"""A visible bind pose must survive if the animated pose is off-screen."""
import ctypes as C
from pathlib import Path
import subprocess,tempfile
H=Path(__file__).resolve().parent
with tempfile.TemporaryDirectory() as d:
 so=Path(d)/'probe.so'
 subprocess.run(['g++','-std=c++17','-O2','-fPIC','-shared',str(H/'cull_probe.cpp'),'-o',str(so)],check=True)
 lib=C.CDLL(str(so));F=C.c_float;D=C.c_double;U=C.c_uint8
 lib.fnaf_cull_probe.argtypes=[C.POINTER(D),C.POINTER(F),C.POINTER(U),C.POINTER(U),C.c_int,C.c_int,C.POINTER(F)]
 m=(D*16)(*[1 if i%5==0 else 0 for i in range(16)])
 pos=(F*3)(0,0,0); bones=(U*4)(0,0,0,0);weights=(U*4)(255,0,0,0)
 pal=(F*16)(*[1 if i%5==0 else 0 for i in range(16)]);pal[12]=3
 assert lib.fnaf_cull_probe(m,pos,bones,weights,1,1,pal)==0,'visible bind-pose was rejected'
 m[12]=3
 assert lib.fnaf_cull_probe(m,pos,bones,weights,1,1,pal)==1,'both offscreen should be rejected'
 print('PASS: visible unskinned fallback retained; only fully offscreen model rejected')
