#!/usr/bin/env python3
"""Create small, ordered, SHA-256 verified copies/inserts from two APK ZIPs.
Only byte-identical compressed ZIP payloads are copied from the old APK; the
remaining bytes (headers, new dex/libs and signature block) are literal. The
old file is consumed forward exactly once by the Android decoder.
"""
import sys, struct, hashlib, zipfile
from pathlib import Path
OLD, NEW, PATCH = map(Path,sys.argv[1:4])

def sha_file(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for chunk in iter(lambda:f.read(1<<20),b''):h.update(chunk)
 return h.digest()

def payload(z,entry):
 with open(z.filename,'rb') as f:
  f.seek(entry.header_offset); header=f.read(30)
  if len(header)!=30 or header[:4]!=b'PK\x03\x04':raise ValueError('invalid ZIP header')
  name_len, extra_len=struct.unpack_from('<HH',header,26)
  return entry.header_offset+30+name_len+extra_len

def equal_range(a,ao,b,bo,n):
 a.seek(ao);b.seek(bo)
 while n:
  size=min(n,1<<20); x=a.read(size); y=b.read(size)
  if len(x)!=size or x!=y:return False
  n-=size
 return True

with zipfile.ZipFile(OLD) as oz, zipfile.ZipFile(NEW) as nz, OLD.open('rb') as old, NEW.open('rb') as new:
 oldmap={i.filename:i for i in oz.infolist()}
 selected=[]; last_old_end=0
 for n in sorted(nz.infolist(),key=lambda v:v.header_offset):
  o=oldmap.get(n.filename)
  if not o or not n.compress_size:continue
  if (o.CRC,o.compress_type,o.compress_size,o.file_size)!=(n.CRC,n.compress_type,n.compress_size,n.file_size):continue
  op=payload(oz,o);np=payload(nz,n)
  if op<last_old_end:continue
  if equal_range(old,op,new,np,n.compress_size):
   selected.append((np,op,n.compress_size,n.filename));last_old_end=op+n.compress_size
 ops=[]; pos=0
 for np,op,n,name in selected:
  if np<pos:raise ValueError('overlapping ZIP payloads')
  if np>pos:ops.append(('L',pos,np-pos))
  ops.append(('C',op,n));pos=np+n
 if pos<NEW.stat().st_size:ops.append(('L',pos,NEW.stat().st_size-pos))
 # Coalesce adjacent literal spans (for zero-length omissions).
 merged=[]
 for typ,o,n in ops:
  if merged and typ=='L' and merged[-1][0]=='L' and merged[-1][1]+merged[-1][2]==o:
   t,a,b=merged[-1];merged[-1]=(t,a,b+n)
  else:merged.append((typ,o,n))
 ops=merged
 with PATCH.open('wb') as out:
  out.write(b'FNAFPCH2');out.write(struct.pack('>QQ',OLD.stat().st_size,NEW.stat().st_size));out.write(sha_file(OLD));out.write(sha_file(NEW));out.write(struct.pack('>I',len(ops)))
  for typ,o,n in ops:
   if typ=='C':out.write(b'\x00'+struct.pack('>QQ',o,n))
   else:
    out.write(b'\x01'+struct.pack('>Q',n));new.seek(o);remaining=n
    while remaining:
     block=new.read(min(1<<20,remaining))
     if not block:raise ValueError('short target')
     out.write(block);remaining-=len(block)
 print('Old/new sizes:',OLD.stat().st_size,NEW.stat().st_size)
 print('Matched payloads:',len(selected),'copies:',sum(x[2] for x in ops if x[0]=='C'),'literal:',sum(x[2] for x in ops if x[0]=='L'))
 print('Patch bytes:',PATCH.stat().st_size,'operations:',len(ops),'old SHA:',sha_file(OLD).hex(),'new SHA:',sha_file(NEW).hex())
