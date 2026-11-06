#!/usr/bin/env bash
set -Eeuo pipefail
SDKMAN="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
EMU="$ANDROID_SDK_ROOT/emulator/emulator"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
# Download the already-existing old Actions artifact with this job's own
# read-only checkout token. No token is printed or embedded in the APK.
AUTH_HEADER=$(git config --local --get http.https://github.com/.extraheader)
ENCODED=${AUTH_HEADER##* }
DECODED=$(printf %s "$ENCODED" | base64 --decode)
TOKEN=${DECODED#*:}
unset AUTH_HEADER ENCODED DECODED
OLD_DIR="$RUNNER_TEMP/original-artifact"
mkdir -p "$OLD_DIR"
curl -fsSL --retry 2 -H "Authorization: Bearer $TOKEN" -H 'Accept: application/vnd.github+json' \
  https://api.github.com/repos/yzeka5206-hubn/fnaf-movie-doom-app/actions/artifacts/13467672895/zip \
  -o "$RUNNER_TEMP/original-artifact.zip"
unset TOKEN
HELPER=${1:?Pass the APK built by this CI job}
[ -f "$HELPER" ] || { echo 'Built updater APK not found'; exit 1; }
python3 - "$RUNNER_TEMP/original-artifact.zip" "$OLD_DIR" <<'PYOLD'
import os,sys,zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
 names=[n for n in z.namelist() if n.endswith('doom-debug.apk') and not n.startswith('/') and '..' not in n.split('/')]
 if len(names)!=1: raise SystemExit('Unexpected original artifact structure')
 with z.open(names[0]) as src,open(os.path.join(sys.argv[2],'old.apk'),'wb') as dst:
  import shutil;shutil.copyfileobj(src,dst,1024*1024)
PYOLD
OLD="$OLD_DIR/old.apk"
echo '6872aa42e59cfd66bfceae35cd1c289382f533dd2b91d57ce0d8bf91e7a256a4  '"$OLD" | sha256sum -c -
cleanup() {
  rc=$?
  echo "=== helper test cleanup (exit $rc); emulator log tail ==="
  tail -100 "$RUNNER_TEMP/helper-emulator.txt" 2>/dev/null || true
  timeout 10 "$ADB" emu kill >/dev/null 2>&1 || true
  [ -z "${EPID:-}" ] || kill "$EPID" >/dev/null 2>&1 || true
}
trap cleanup EXIT
[ -e /dev/kvm ] || { echo 'No /dev/kvm; helper integration test unavailable'; exit 1; }
sudo chmod a+rw /dev/kvm
echo "=== KVM diagnostic ==="
ls -l /dev/kvm
echo "=== Freeing unused software on disposable CI runner ==="
df -h "$RUNNER_TEMP"
sudo rm -rf "$ANDROID_SDK_ROOT/ndk" /usr/share/dotnet /opt/ghc /usr/local/share/boost || true
df -h "$RUNNER_TEMP"
"$SDKMAN" 'emulator' 'system-images;android-34;google_apis;x86_64' > "$RUNNER_TEMP/helper-sdk.txt" 2>&1 || { tail -40 "$RUNNER_TEMP/helper-sdk.txt"; exit 1; }
"$EMU" -accel-check || true
"$ADB" start-server
export ANDROID_AVD_HOME="$RUNNER_TEMP/fnaf-avds"
mkdir -p "$ANDROID_AVD_HOME/helperAndroid14.avd"
cat > "$ANDROID_AVD_HOME/helperAndroid14.ini" <<EOF
avd.ini.encoding=UTF-8
path=$ANDROID_AVD_HOME/helperAndroid14.avd
target=android-34
EOF
cat > "$ANDROID_AVD_HOME/helperAndroid14.avd/config.ini" <<EOF
AvdId=helperAndroid14
abi.type=x86_64
avd.ini.encoding=UTF-8
disk.dataPartition.size=4G
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.lcd.density=320
hw.lcd.width=1080
hw.lcd.height=1920
hw.ramSize=2048
hw.keyboard=yes
hw.gpu.enabled=yes
image.sysdir.1=system-images/android-34/google_apis/x86_64/
tag.display=Google APIs
tag.id=google_apis
PlayStore.enabled=false
vm.heapSize=256
EOF
"$EMU" -list-avds
"$EMU" -avd helperAndroid14 -no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect -memory 2048 -cores 2 > "$RUNNER_TEMP/helper-emulator.txt" 2>&1 & EPID=$!
timeout 120 "$ADB" wait-for-device || { echo "ADB did not connect"; "$ADB" devices -l; exit 1; }
BOOT=$("$ADB" shell getprop sys.boot_completed | tr -d '\r')
if [ "$BOOT" != 1 ]; then
  timeout 180 python3 - "$ADB" <<'PYBOOT'
import subprocess,sys
p=subprocess.Popen([sys.argv[1],"logcat","-b","events","-v","brief"],stdout=subprocess.PIPE,text=True)
try:
 for line in p.stdout:
  if any(x in line for x in ("boot_progress_enable_screen","boot_progress_launcher","am_boot_complete")):
   print(line.strip(),flush=True);break
 else: raise RuntimeError("logcat ended before boot")
finally:
 p.terminate()
 try: p.wait(timeout=5)
 except subprocess.TimeoutExpired: p.kill();p.wait()
PYBOOT
fi
printf '=== Android version / install actual original app and helper ===\n'
"$ADB" shell getprop ro.build.version.release
"$ADB" install --no-streaming "$OLD"
# Sentinel demonstrates the original package's private data survives preparation
# and installation of the separately packaged animation test.
"$ADB" shell run-as com.fnaf.moviedoom.animationtest sh -c "'mkdir -p files; echo old-data-preserved > files/updater-ci-sentinel.txt'"
"$ADB" install "$HELPER"
"$ADB" logcat -c
"$ADB" shell wm dismiss-keyguard || true
"$ADB" shell am start -W -n com.fnaf.moviedoom.optimizeupdater/com.fnaf.moviedoom.repair.MainActivity
"$ADB" shell uiautomator dump /sdcard/window.xml >/dev/null
"$ADB" shell cat /sdcard/window.xml > "$RUNNER_TEMP/helper-window.xml"
read X Y < <(python3 - "$RUNNER_TEMP/helper-window.xml" <<'PY'
import sys,xml.etree.ElementTree as E,re
root=E.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
 s=node.attrib.get('text','')
 if "HAZIRLA" in s:
  n=list(map(int,re.findall(r'\d+',node.attrib['bounds'])));print((n[0]+n[2])//2,(n[1]+n[3])//2);break
else: raise SystemExit('prepare button not found')
PY
)
printf '=== Tapping installed-APK preparation button at %s,%s ===\n' "$X" "$Y"
"$ADB" shell input tap "$X" "$Y"
# The production APK has no CI-only log statements. Wait for its *published*
# MediaStore output instead, then verify its bytes and signature below.
if ! python3 - "$ADB" <<'PYPUBLISHED'
import subprocess,sys,time
adb=sys.argv[1]; start=time.monotonic(); end=start+300; sampled=set()
while time.monotonic()<end:
 result=subprocess.run([adb,'shell','test','-f','/sdcard/Download/FNAF_Optimize_Test.apk'], stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 if result.returncode==0:
  print('Published output detected after',round(time.monotonic()-start),'seconds',flush=True);break
 elapsed=time.monotonic()-start
 for mark in (30,120,240):
  if elapsed>=mark and mark not in sampled:
   sampled.add(mark); print('=== updater diagnosis at',mark,'seconds ===',flush=True)
   subprocess.run([adb,'shell','run-as','com.fnaf.moviedoom.optimizeupdater','ls','-la','/sdcard/Download'],timeout=20,check=False)
   pid=subprocess.run([adb,'shell','pidof','com.fnaf.moviedoom.optimizeupdater'],capture_output=True,text=True,timeout=10).stdout.strip()
   if pid:
    try: subprocess.run([adb,'shell','debuggerd','-b',pid],timeout=20,check=False)
    except subprocess.TimeoutExpired: print('debuggerd timed out',flush=True)
 time.sleep(2)
else: raise SystemExit('No published APK after 300 seconds')
PYPUBLISHED
then
  echo 'Updater did not publish the target file'
  "$ADB" shell uiautomator dump /sdcard/helper-after.xml >/dev/null || true
  "$ADB" shell cat /sdcard/helper-after.xml || true
  "$ADB" logcat -d -s ActivityTaskManager:I AndroidRuntime:E '*:S' | tail -120
  exit 1
fi
"$ADB" shell uiautomator dump /sdcard/helper-after.xml >/dev/null || true
"$ADB" shell cat /sdcard/helper-after.xml || true
"$ADB" pull /sdcard/Download/FNAF_Optimize_Test.apk "$RUNNER_TEMP/repaired-fixture.apk" >/dev/null
echo 'f55c97179051b1ae5b36a255baa79d4c33e03c51ae0c15b4b0628f7a68ff9286  '"$RUNNER_TEMP/repaired-fixture.apk" | sha256sum -c -
"$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner" verify --verbose "$RUNNER_TEMP/repaired-fixture.apk" | head -4
"$ADB" shell pm path com.fnaf.moviedoom.animationtest
# The target is a distinct package: both should coexist and old app data remain.
"$ADB" install --no-streaming "$RUNNER_TEMP/repaired-fixture.apk"
"$ADB" shell pm path com.fnaf.moviedoom.animationtest
"$ADB" shell pm path com.fnaf.moviedoom.optimized
"$ADB" shell run-as com.fnaf.moviedoom.animationtest cat files/updater-ci-sentinel.txt | tr -d '\r' | grep -Fx 'old-data-preserved'
printf 'PASS: REAL old APK reconstructed on Android 14 to exact target SHA, valid signature, both apps coexist, old private data intact\n' 
