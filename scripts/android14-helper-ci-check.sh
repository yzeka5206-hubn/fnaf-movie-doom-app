#!/usr/bin/env bash
set -Eeuo pipefail
SDKMAN="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
AVDMAN="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
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
"$EMU" -accel-check || true
"$ADB" start-server
"$SDKMAN" 'system-images;android-34;google_atd;x86_64' > "$RUNNER_TEMP/helper-sdk.txt" 2>&1 || { tail -40 "$RUNNER_TEMP/helper-sdk.txt"; exit 1; }
printf 'no\n' | "$AVDMAN" create avd --force --name helperAndroid14 --package 'system-images;android-34;google_atd;x86_64' --device 'pixel_2' > "$RUNNER_TEMP/helper-avd.txt" 2>&1
"$EMU" -avd helperAndroid14 -no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect -memory 1536 -cores 2 -verbose > "$RUNNER_TEMP/helper-emulator.txt" 2>&1 & EPID=$!
timeout 120 "$ADB" wait-for-device || { echo "ADB did not connect"; "$ADB" devices -l; exit 1; }
BOOT=$("$ADB" shell getprop sys.boot_completed | tr -d '\r')
if [ "$BOOT" != 1 ]; then
  timeout 240 bash -c '"$1" logcat -b events -v brief | grep -m 1 -E "boot_progress_enable_screen|boot_progress_launcher|am_boot_complete"' _ "$ADB" || { tail -60 "$RUNNER_TEMP/helper-emulator.txt"; exit 1; }
fi
printf '=== Android version / install fixtures ===\n'
"$ADB" shell getprop ro.build.version.release
"$ADB" install "$FIX/old.apk"
"$ADB" install "$HELPER"
"$ADB" logcat -c
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
timeout 60 bash -c '"$1" logcat -v brief -s FnafRepairCI:I "*:S" | grep -m1 "FNAF_REPAIR_"' _ "$ADB" > "$RUNNER_TEMP/helper-result.txt" & LPID=$!
"$ADB" shell input tap "$X" "$Y"
wait "$LPID" || { echo 'No patch completion log'; "$ADB" logcat -d -v brief | tail -120; exit 1; }
cat "$RUNNER_TEMP/helper-result.txt"
grep -q FNAF_REPAIR_READY "$RUNNER_TEMP/helper-result.txt"
"$ADB" pull /sdcard/Download/FNAF_Menu_Android14.apk "$RUNNER_TEMP/repaired-fixture.apk" >/dev/null
cmp "$FIX/expected.apk" "$RUNNER_TEMP/repaired-fixture.apk"
"$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner" verify --verbose "$RUNNER_TEMP/repaired-fixture.apk" | head -4
"$ADB" shell pm path com.fnaf.moviedoom.menutest
printf 'PASS: Android 14 installed APK read, verified patch, MediaStore output and unchanged original app\n'
