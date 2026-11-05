#!/usr/bin/env bash
set -Eeuo pipefail
SDKMAN="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
EMU="$ANDROID_SDK_ROOT/emulator/emulator"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
FIX="$PWD/doom/repair/fixtures"
HELPER="$PWD/doom/build/outputs/apk/debug/FNAF-Menu-Onarici.apk"
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
"$SDKMAN" 'emulator' 'system-images;android-34;google_atd;x86_64' > "$RUNNER_TEMP/helper-sdk.txt" 2>&1 || { tail -40 "$RUNNER_TEMP/helper-sdk.txt"; exit 1; }
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
disk.dataPartition.size=2G
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.lcd.density=320
hw.lcd.width=1080
hw.lcd.height=1920
hw.ramSize=1536
hw.keyboard=yes
hw.gpu.enabled=yes
image.sysdir.1=system-images/android-34/google_atd/x86_64/
tag.display=Google ATD
tag.id=google_atd
PlayStore.enabled=false
vm.heapSize=256
EOF
"$EMU" -list-avds
"$EMU" -avd helperAndroid14 -no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect -memory 1536 -cores 2 > "$RUNNER_TEMP/helper-emulator.txt" 2>&1 & EPID=$!
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
printf '=== Android version / install fixtures ===\n'
"$ADB" shell getprop ro.build.version.release
"$ADB" install "$FIX/old.apk"
"$ADB" install "$HELPER"
"$ADB" logcat -c
"$ADB" shell wm dismiss-keyguard || true
"$ADB" shell am start -W -n com.fnaf.moviedoom.repair/.MainActivity
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
timeout 60 python3 - "$ADB" > "$RUNNER_TEMP/helper-result.txt" <<'PYRESULT' & LPID=$!
import subprocess,sys
p=subprocess.Popen([sys.argv[1],"logcat","-v","brief","-s","FnafRepairCI:I","*:S"],stdout=subprocess.PIPE,text=True)
try:
 for line in p.stdout:
  if "FNAF_REPAIR_" in line:
   print(line.strip(),flush=True);break
 else: raise RuntimeError("logcat ended before repair result")
finally:
 p.terminate()
 try: p.wait(timeout=5)
 except subprocess.TimeoutExpired: p.kill();p.wait()
PYRESULT
"$ADB" shell input tap "$X" "$Y"
wait "$LPID" || { echo 'No patch completion log'; "$ADB" logcat -d -v brief | tail -120; exit 1; }
cat "$RUNNER_TEMP/helper-result.txt"
grep -q FNAF_REPAIR_READY "$RUNNER_TEMP/helper-result.txt"
"$ADB" pull /sdcard/Download/FNAF_Menu_Android14.apk "$RUNNER_TEMP/repaired-fixture.apk" >/dev/null
cmp "$FIX/expected.apk" "$RUNNER_TEMP/repaired-fixture.apk"
"$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner" verify --verbose "$RUNNER_TEMP/repaired-fixture.apk" | head -4
"$ADB" shell pm path com.fnaf.moviedoom.menutest
printf 'PASS: Android 14 installed APK read, verified patch, MediaStore output and unchanged original app\n'
