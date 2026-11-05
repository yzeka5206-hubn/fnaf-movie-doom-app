#!/usr/bin/env bash
# Optional host smoke tests: compile Game.kt against Android 36, then run the
# real overlay logic and SDLInputConnection using minimal JVM Android fakes.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
ANDROID_JAR=${ANDROID_JAR:-"${ANDROID_HOME:-}/platforms/android-36/android.jar"}
KOTLINC=${KOTLINC:-$(command -v kotlinc || true)}
if [[ ! -f "$ANDROID_JAR" || -z "$KOTLINC" || ! -x "$KOTLINC" ]]; then
  echo "Set ANDROID_JAR to Android 36 android.jar and KOTLINC to Kotlin 2.2.x's compiler." >&2
  exit 2
fi
KOTLIN_HOME=$(cd "$(dirname "$KOTLINC")/.." && pwd)
STDLIB="$KOTLIN_HOME/lib/kotlin-stdlib.jar"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
python3 "$ROOT/scripts/tests/create_fake_android.py" "$T/fake-src"
mkdir -p "$T/fakes" "$T/game"
find "$T/fake-src" -name '*.java' -print0 | xargs -0 javac -source 11 -target 11 -d "$T/fakes"
"$KOTLINC" -jvm-target 11 -classpath "$ANDROID_JAR:$T/fakes" \
  "$ROOT/doom/src/main/java/com/msa/freedoom/FnafInputState.kt" \
  "$ROOT/doom/src/main/java/com/msa/freedoom/Game.kt" -d "$T/game"
javac -source 11 -target 11 -cp "$T/fakes:$T/game:$STDLIB" -d "$T/fakes" \
  "$ROOT/scripts/tests/GameOverlayHostTest.java"
java -cp "$T/fakes:$T/game:$STDLIB" com.msa.freedoom.GameOverlayHostTest
ROOT="$ROOT" OUT="$T/SDLInputConnection.java" python3 - <<'PY'
import os
from pathlib import Path
p = Path(os.environ['ROOT']) / 'doom/src/main/java/org/libsdl/app2012/SDLActivity.java'
src = p.read_text()
a = src.index('class SDLInputConnection extends BaseInputConnection {')
b = src.index('\ninterface SDLClipboardHandler', a)
header = ('package org.libsdl.app2012;\nimport android.view.View;\n'
          'import android.view.KeyEvent;\nimport android.view.inputmethod.BaseInputConnection;\n')
Path(os.environ['OUT']).write_text(header + src[a:b] + '\n')
PY
javac -source 11 -target 11 -cp "$ANDROID_JAR:$T/fakes" -d "$T/java-android" "$T/SDLInputConnection.java"
javac -source 11 -target 11 -cp "$T/fakes" -d "$T/fakes" "$T/SDLInputConnection.java" \
  "$ROOT/scripts/tests/SoftKeyboardHostTest.java"
java -cp "$T/fakes" org.libsdl.app2012.SoftKeyboardHostTest
# Also compile and exercise the real native-message handler. The Android jar
# comes first at compile time; fake Android classes execute it on the host.
mkdir -p "$T/bridge"
javac -encoding UTF-8 -source 11 -target 11 -cp "$ANDROID_JAR:$T/fakes" -d "$T/bridge" \
  "$ROOT/doom/src/main/java/com/msa/freedoom/FnafLaunchProfile.java" \
  "$ROOT/doom/src/main/java/org/libsdl/app/SDLOpenTouch.java"
javac -encoding UTF-8 -source 11 -target 11 -cp "$T/bridge:$T/fakes" -d "$T/bridge" \
  "$ROOT/scripts/tests/KeyboardBridgeHostTest.java"
java -cp "$T/bridge:$T/fakes" org.libsdl.app.KeyboardBridgeHostTest
