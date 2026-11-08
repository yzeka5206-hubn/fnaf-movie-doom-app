#!/usr/bin/env bash
# Android API-36 compile and typed intent roundtrip on JVM fakes, not a device test.
set -euo pipefail
R=$(cd "$(dirname "$0")/../.." && pwd)
ANDROID_JAR=${ANDROID_JAR:?}
KOTLINC=${KOTLINC:?}
JAVA_OPTS=${JAVA_OPTS:--Xmx256m}; export JAVA_OPTS
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
mkdir -p "$T/stubs/org/libsdl/app2012" "$T/stubs/com/msa/freedoom" "$T/classes" "$T/runtime/android/content" "$T/runtime/com/msa/freedoom"
cat > "$T/stubs/org/libsdl/app2012/SDLActivity.java" <<'EOF'
package org.libsdl.app2012;
public class SDLActivity extends android.app.Activity {
 public boolean useOpenTouch(){return true;} public String[] getLibraries(){return null;}
 public void setOrientationBis(int w,int h,boolean r,String s){}
 public void runLauncherAfterSdlSetup(String[] a){}
 public String getMainSharedObject(){return null;} public String[] getArguments(){return null;}
}
EOF
cat > "$T/stubs/com/msa/freedoom/Mocks.java" <<'EOF'
package com.msa.freedoom;
class FnafOptionsActivity extends android.app.Activity {}
class MenuCrashReporter {public static void install(android.content.Context x){}}
class AppSettings {public static void reloadSettings(android.app.Application x){}}
class Utils {public static void setImmersionMode(android.app.Activity x){} public static void onWindowFocusChanged(android.app.Activity x,boolean b){}}
EOF
javac -source 11 -target 11 -cp "$ANDROID_JAR" -d "$T/classes" "$T/stubs/org/libsdl/app2012/SDLActivity.java" "$T/stubs/com/msa/freedoom/Mocks.java" "$R/doom/src/main/java/com/msa/freedoom/FnafLaunchProfile.java"
"$KOTLINC" -jvm-target 11 -classpath "$ANDROID_JAR:$T/classes" \
 "$R/doom/src/main/java/com/msa/freedoom/FnafMenuSelection.kt" \
 "$R/doom/src/main/java/com/msa/freedoom/OriginalMenuActivity.kt" -d "$T/classes"
cat > "$T/runtime/android/content/Intent.java" <<'EOF'
package android.content;
import java.util.*;
public class Intent {
 private final Map<String,Object> d=new HashMap<>();
 public Intent putExtra(String k,boolean v){d.put(k,v);return this;}
 public Intent putExtra(String k,int v){d.put(k,v);return this;}
 public Intent putExtra(String k,String v){d.put(k,v);return this;}
 public boolean getBooleanExtra(String k,boolean v){return d.containsKey(k)?(Boolean)d.get(k):v;}
 public int getIntExtra(String k,int v){return d.containsKey(k)?(Integer)d.get(k):v;}
 public String getStringExtra(String k){return (String)d.get(k);}
}
EOF
cat > "$T/runtime/com/msa/freedoom/MenuRoundTrip.java" <<'EOF'
package com.msa.freedoom;
import android.content.Intent;
public class MenuRoundTrip {
 public static void main(String[] a){
  int count=0;
  for(boolean exterior:new boolean[]{false,true}) for(boolean textures:new boolean[]{false,true})
   for(boolean camera:new boolean[]{false,true}) for(int res:new int[]{480,720}) {
    var in=new FnafMenuSelection("NIGHT1",0,0,0,0,0,0,res,exterior,textures,camera);
    Intent intent=new Intent(); in.writeTo(intent); var out=FnafMenuSelection.Companion.fromIntent(intent);
    if(!in.equals(out)) throw new AssertionError("typed menu options changed across Intent"); count++;
   }
  Intent missing=new Intent();
  if(FnafMenuSelection.Companion.fromIntent(missing)!=null) throw new AssertionError("bad intent accepted");
  Intent legacy=new Intent().putExtra("fnaf_menu_selection",true).putExtra("fnaf_map","NIGHT1")
    .putExtra("fnaf_difficulty",0).putExtra("fnaf_streamer",0).putExtra("fnaf_graphics",0)
    .putExtra("fnaf_shaders",0).putExtra("fnaf_halloween",0).putExtra("fnaf_christmas",0).putExtra("fnaf_resolution",480);
  var old=FnafMenuSelection.Companion.fromIntent(legacy);
  if(old==null || !old.getLightTextures() || old.getCameraSaver()) throw new AssertionError("legacy defaults");
  System.out.println("PASS original menu compiled against Android36; " + count + " cross-process Intent option roundtrips");
 }
}
EOF
javac -source 11 -target 11 -cp "$T/classes:$ANDROID_JAR:$(dirname "$KOTLINC")/../lib/kotlin-stdlib.jar" -d "$T/runtime" "$T/runtime/android/content/Intent.java" "$T/runtime/com/msa/freedoom/MenuRoundTrip.java"
java -cp "$T/runtime:$T/classes:$(dirname "$KOTLINC")/../lib/kotlin-stdlib.jar" com.msa.freedoom.MenuRoundTrip
