#!/usr/bin/env python3
"""Check the shipping predicate against all real NIGHT1..5 UDMF portals.

This tests classification, not rendering, physics, audio, or phone FPS.
"""
import collections, hashlib, re, struct, subprocess, sys
from pathlib import Path

P=Path(__file__).resolve().parent
wad=Path(sys.argv[1])
expected='553ce0056d8806d87a78832d107e072374a5d78fded09937c3686ee84a74d6d8'
assert hashlib.sha256(wad.read_bytes()).hexdigest()==expected, 'Unexpected game WAD; fail open'
magic,n,offset=struct.unpack_from('<4sII',wad.read_bytes()); assert magic==b'PWAD'
with wad.open('rb') as f:
 f.seek(offset); records=[struct.unpack('<II8s',f.read(16)) for _ in range(n)]
 def name(r):return r[2].split(b'\0')[0].decode('ascii')
 def map_text(mapname):
  i=next(i for i,r in enumerate(records) if name(r)==mapname)
  assert name(records[i+1])=='TEXTMAP'; off,sz,_=records[i+1];f.seek(off);return f.read(sz).decode()
 texts={x:map_text(x) for x in ('NIGHT1','NIGHT2','NIGHT3','NIGHT4','NIGHT5')}
pat=re.compile(r'(?ms)^(vertex|linedef)\s*//\s*(\d+)\s*\{(.*?)\}')
attrs=re.compile(r'(?m)^\s*(\w+)\s*=\s*([^;]+);')
def parse(s):
 out=collections.defaultdict(list)
 for k,i,b in pat.findall(s):
  assert int(i)==len(out[k]);d={}
  for a,v in attrs.findall(b):
   v=v.strip()
   try:d[a]=float(v) if '.' in v else int(v)
   except ValueError:d[a]=v
  out[k].append(d)
 return out

probe=P/'probe.tmp'
subprocess.run(['g++','-std=c++17','-O2','-Wall','-Wextra','-Werror',str(P/'probe.cpp'),'-o',str(probe)],check=True)
lines=[];expected_results=[];counts={}
try:
 for mapname,text in texts.items():
  m=parse(text);byid={l['id']:l for l in m['linedef'] if l.get('id',0)}
  accepted=0;ignored=0
  for line in m['linedef']:
   if line.get('special')!=156 or not line.get('arg0'):continue
   target=byid[line['arg0']]; sid=line.get('id',0);did=target.get('id',0)
   def xy(l):
    a,b=[m['vertex'][l[k]] for k in ('v1','v2')]
    return ((a['x']+b['x'])/2,(a['y']+b['y'])/2)
   sx,sy=xy(line);dx,dy=xy(target)
   want=(sid,did) in {(101,100),(103,102),(105,104),(106,108),(107,109)} or (sid==0 and did in (20,21))
   lines.append(f'p {mapname} {sid} {did} {sx} {sy} {dx} {dy}');expected_results.append((int(want),mapname,sid,did,sx,sy,dx,dy))
   accepted+=int(want);ignored+=int(not want)
  assert accepted==21, (mapname,accepted)
  assert ignored>=3,(mapname,ignored)
  counts[mapname]=(accepted,ignored)
 result=subprocess.run([str(probe)],input='\n'.join(lines)+'\n',text=True,check=True,capture_output=True).stdout.splitlines()
 assert len(result)==len(expected_results)
 for actual,expected_row in zip(result,expected_results): assert int(actual)==expected_row[0],(actual,expected_row)
 print('PASS exact WAD: per night 5 interior entrance portals + 16 exterior display-cell portals recognized; unrelated portals retained',counts)
finally: probe.unlink(missing_ok=True)
