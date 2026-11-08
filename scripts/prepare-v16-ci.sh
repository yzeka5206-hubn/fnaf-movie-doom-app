#!/usr/bin/env bash
# Generate and replay the offline v15 -> signed-v16 helper payload in CI.
# The game is signed elsewhere with its existing key; this script never signs it.
set -Eeuo pipefail
OLD=${1:?old v15 APK path required}
NEW=${2:?signed v16 APK path required}
WORK=${3:?temporary work directory required}
EXPECTED=${FNAF_EXPECTED_SOURCE_SHA:-3ae61eee3afd2fd1b55c73cefff22d5c540a000d4961d04e35ee0edbb48c1fe5}
[[ $EXPECTED =~ ^[0-9a-f]{64}$ ]] || { echo 'Invalid expected source SHA'; exit 1; }
test -s "$OLD" && test -s "$NEW"
echo "$EXPECTED  $OLD" | sha256sum -c -
python3 -m unittest discover -s scripts/tests -p 'test_refined_delta.py' -v
mkdir -p "$WORK/classes" doom/repair/assets
javac -encoding UTF-8 -d "$WORK/classes" doom/repair/java/com/fnaf/moviedoom/repair/PatchEngine.java doom/repair/tests/Test.java
java -Xmx128m -cp "$WORK/classes" com.fnaf.moviedoom.repair.Test
DELTA=doom/repair/assets/repair-v15.dat
python3 scripts/make-apk-repair-delta-refined.py "$OLD" "$NEW" "$DELTA"
REPLAY="$WORK/replay.apk"
java -Xmx128m -cp "$WORK/classes" com.fnaf.moviedoom.repair.Test "$OLD" "$DELTA" "$REPLAY"
cmp "$NEW" "$REPLAY"
rm "$REPLAY"
python3 - "$OLD" "$NEW" "$DELTA" "$WORK/metadata.json" <<'PY'
from pathlib import Path
import hashlib, json, struct, sys
old,new,delta,meta=map(Path,sys.argv[1:])
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
source,target,patch=sha(old),sha(new),sha(delta)
with delta.open('rb') as f:header=f.read(92)
if len(header)!=92 or header[:8]!=b'FNAFPCH2':raise SystemExit('Invalid patch header')
a,b=struct.unpack('>QQ',header[8:24]);count=struct.unpack('>I',header[88:92])[0]
if a!=old.stat().st_size or b!=new.stat().st_size:raise SystemExit('Patch size mismatch')
if header[24:56].hex()!=source or header[56:88].hex()!=target:raise SystemExit('Patch SHA header mismatch')
if not 0<count<=16384:raise SystemExit('Patch operation count out of range')
data={'source_sha256':source,'target_sha256':target,'delta_sha256':patch,
      'old_bytes':a,'new_bytes':b,'delta_bytes':delta.stat().st_size,'operations':count}
meta.write_text(json.dumps(data,indent=2)+'\n')
print('PASS exact old/new APK replay, SHA-256 headers and operation bounds:',json.dumps(data,sort_keys=True))
PY
if [[ -n ${GITHUB_OUTPUT:-} ]]; then
  TARGET_SHA=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["target_sha256"])' "$WORK/metadata.json")
  echo "target_sha=$TARGET_SHA" >> "$GITHUB_OUTPUT"
fi
