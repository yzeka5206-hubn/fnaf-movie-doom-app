#!/usr/bin/env bash
# Tests the real menu view against Android API signatures and executable JVM fakes.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
ANDROID_JAR=${ANDROID_JAR:-"${ANDROID_HOME:-}/platforms/android-36/android.jar"}
KOTLINC=${KOTLINC:-$(command -v kotlinc || true)}
if [[ ! -f "$ANDROID_JAR" || -z "$KOTLINC" || ! -x "$KOTLINC" ]]; then
  echo "Set ANDROID_JAR to Android 36 android.jar and KOTLINC to Kotlin 2.2.x." >&2
  exit 2
fi
KOTLIN_HOME=$(cd "$(dirname "$KOTLINC")/.." && pwd)
STDLIB="$KOTLIN_HOME/lib/kotlin-stdlib.jar"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
python3 "$ROOT/scripts/tests/create_fake_menu_android.py" "$T/fakes-src"
mkdir -p "$T/fakes" "$T/android-api" "$T/kotlin"
find "$T/fakes-src" -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -source 11 -target 11 -d "$T/fakes"
# The Android SDK comes first here, so typos against the real View API fail.
javac -encoding UTF-8 -source 11 -target 11 -cp "$ANDROID_JAR:$T/fakes" -d "$T/android-api" \
  "$ROOT/doom/src/main/java/com/msa/freedoom/FnafMenuGeometry.java" \
  "$ROOT/doom/src/main/java/com/msa/freedoom/FnafMenuView.java"
# Execute the same View source against test doubles to check layout and click routing.
javac -encoding UTF-8 -source 11 -target 11 -cp "$T/fakes" -d "$T/fakes" \
  "$ROOT/doom/src/main/java/com/msa/freedoom/FnafMenuGeometry.java" \
  "$ROOT/doom/src/main/java/com/msa/freedoom/FnafMenuView.java" \
  "$ROOT/scripts/tests/FnafMenuHostTest.java"
java -cp "$T/fakes" com.msa.freedoom.FnafMenuHostTest
"$KOTLINC" -jvm-target 11 \
  "$ROOT/doom/src/main/java/com/msa/freedoom/FnafBundledGame.kt" \
  "$ROOT/doom/src/main/java/com/msa/freedoom/ui/launch/LaunchArgs.kt" \
  "$ROOT/scripts/tests/FnafBundledGameTest.kt" -d "$T/kotlin"
java -cp "$T/kotlin:$STDLIB" com.msa.freedoom.FnafBundledGameTestKt
