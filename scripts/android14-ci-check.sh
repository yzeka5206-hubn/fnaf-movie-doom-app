#!/usr/bin/env bash
# Isolated CI diagnostic on Android 14. No alterations to the APK under test.
set -Eeuo pipefail
OUT="$PWD/doom/build/outputs/apk/debug"
SDKMAN="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
EMU="$ANDROID_SDK_ROOT/emulator/emulator"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
APK="$OUT/doom-debug.apk"
LOG="$RUNNER_TEMP/menu-diagnostic-log.txt"
SCREEN="$RUNNER_TEMP/menu-diagnostic-screen.png"
: >"$LOG"
cleanup() {
  rc=$?
  echo "=== Diagnostic cleanup status $rc ==="
  tail -50 "$RUNNER_TEMP/ci-emulator.txt" 2>/dev/null || true
  if [ -f "$LOG" ]; then cp "$LOG" "$OUT/CI-diagnostic-log.txt.apk"; fi
  if [ -f "$SCREEN" ] && [ -s "$SCREEN" ]; then cp "$SCREEN" "$OUT/CI-diagnostic-screen.png.apk"; fi
  timeout 10 "$ADB" emu kill >/dev/null 2>&1 || true
  if [ -n "${EPID:-}" ]; then kill "$EPID" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT
[ -f "$APK" ] || { echo 'No APK to test'; exit 1; }
[ -e /dev/kvm ] || { echo 'No /dev/kvm; CI Android 14 diagnostic skipped'; exit 0; }
sudo chmod a+rw /dev/kvm
printf '=== Freeing unused software on this disposable GitHub runner ===\n'
df -h "$RUNNER_TEMP"
sudo rm -rf "$ANDROID_SDK_ROOT/ndk" /usr/share/dotnet /opt/ghc /usr/local/share/boost || true
df -h "$RUNNER_TEMP"
printf '=== Installing Android 14 Google APIs image ===\n'
"$SDKMAN" 'emulator' 'system-images;android-34;google_apis;x86_64' > "$RUNNER_TEMP/ci-sdk-install.txt" 2>&1 || { tail -40 "$RUNNER_TEMP/ci-sdk-install.txt"; exit 1; }
"$EMU" -accel-check || true
"$ADB" start-server
export ANDROID_AVD_HOME="$RUNNER_TEMP/fnaf-avds"
mkdir -p "$ANDROID_AVD_HOME/fnafAndroid14.avd"
cat > "$ANDROID_AVD_HOME/fnafAndroid14.ini" <<EOF
avd.ini.encoding=UTF-8
path=$ANDROID_AVD_HOME/fnafAndroid14.avd
target=android-34
EOF
cat > "$ANDROID_AVD_HOME/fnafAndroid14.avd/config.ini" <<EOF
AvdId=fnafAndroid14
abi.type=x86_64
avd.ini.encoding=UTF-8
disk.dataPartition.size=3G
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
"$EMU" -avd fnafAndroid14 -no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect -memory 2048 -cores 2 > "$RUNNER_TEMP/ci-emulator.txt" 2>&1 & EPID=$!
printf '=== Waiting for Android device ===\n'
timeout 180 "$ADB" wait-for-device || { echo "ADB did not connect"; "$ADB" devices -l; exit 1; }
# Listen for the standard boot event; avoid hammering the emulator with polling.
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
printf '\n=== Device and native bridge ===\n'
"$ADB" shell getprop ro.build.version.release
"$ADB" shell getprop ro.product.cpu.abilist
"$ADB" shell getprop ro.dalvik.vm.native.bridge
printf '\n=== Install and launch fixed APK ===\n'
"$ADB" install --no-streaming "$APK"
"$ADB" logcat -c
"$ADB" shell am start -W -n com.fnaf.moviedoom.menutest/com.msa.freedoom.FnafMenuActivity
# Capture startup and timed launcher frames for 35 seconds, then inspect state.
timeout 35 "$ADB" logcat -v threadtime > "$LOG" || true
printf '\n=== Menu process after startup ===\n'
"$ADB" shell pidof com.fnaf.moviedoom.menutest:OriginalMenu || true
"$ADB" shell ps -A | grep -E 'fnaf.moviedoom.menutest|PID' || true
"$ADB" shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' || true
printf '\n=== Relevant startup / crash events ===\n'
grep -n -i -E -C 4 'FATAL EXCEPTION|AndroidRuntime|Fatal signal|SecurityException|OriginalMenuActivity|native bridge|Chowdren|FnafMenuRunner|UnsatisfiedLinkError' "$LOG" | tail -220 || true
"$ADB" exec-out screencap -p > "$SCREEN" || true
printf '\n=== Diagnostic screenshot bytes: '; wc -c < "$SCREEN"
printf '\n=== Local crash report file names (if any) ===\n'
"$ADB" shell run-as com.fnaf.moviedoom.menutest find files -maxdepth 2 -type f 2>/dev/null | head -30 || true
printf '\n=== Android 14 diagnostic finished; emulator != Xiaomi device ===\n'
