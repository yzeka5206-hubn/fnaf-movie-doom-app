#!/usr/bin/env bash
# Executes real Kotlin launcher buttons with deliberately small JVM Android fakes.
# A smoke test for wiring and persisted options, not a replacement for device tests.
set -euo pipefail
R=$(cd "$(dirname "$0")/../.." && pwd)
ANDROID_JAR=${ANDROID_JAR:?}; KOTLINC=${KOTLINC:?}; export JAVA_OPTS=${JAVA_OPTS:--Xmx256m}
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
python3 "$R/scripts/tests/create_fake_android.py" "$T/src" >/dev/null
python3 - "$T/src" <<'PY'
from pathlib import Path
import sys
r=Path(sys.argv[1])
for old in ('com/msa/freedoom/AppSettings.java', 'com/msa/freedoom/Utils.java'):
 (r/old).unlink(missing_ok=True)
def w(path,text):
 p=r/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
w('android/content/Intent.java','''package android.content; import java.util.*; import android.net.Uri;
public class Intent { public static final String ACTION_VIEW="view"; private final Map<String,Object> d=new HashMap<>();
public Intent(){} public Intent(Context c,Class<?> z){} public Intent(String s,Uri u){}
public Intent putExtra(String k,boolean v){d.put(k,v);return this;} public Intent putExtra(String k,int v){d.put(k,v);return this;} public Intent putExtra(String k,String v){d.put(k,v);return this;}
public boolean getBooleanExtra(String k,boolean v){return d.containsKey(k)?(Boolean)d.get(k):v;} public int getIntExtra(String k,int v){return d.containsKey(k)?(Integer)d.get(k):v;} public String getStringExtra(String k){return (String)d.get(k);} }
''')
w('android/content/Context.java','''package android.content; import java.util.*; public class Context {
public static final int MODE_PRIVATE=0; private static final Map<String,Object> prefs=new HashMap<>();
private final android.content.res.Resources res=new android.content.res.Resources(); public android.content.res.Resources getResources(){return res;}
public SharedPreferences getSharedPreferences(String x,int mode){return new SharedPreferences(){
public boolean getBoolean(String k,boolean d){return prefs.containsKey(k)?(Boolean)prefs.get(k):d;} public int getInt(String k,int d){return prefs.containsKey(k)?(Integer)prefs.get(k):d;}
public SharedPreferences.Editor edit(){return new SharedPreferences.Editor(){public SharedPreferences.Editor putBoolean(String k,boolean v){prefs.put(k,v);return this;} public SharedPreferences.Editor putInt(String k,int v){prefs.put(k,v);return this;} public void apply(){} public boolean commit(){return true;} };}};}}
''')
w('android/content/SharedPreferences.java','''package android.content; public interface SharedPreferences { boolean getBoolean(String k,boolean d); int getInt(String k,int d); Editor edit(); interface Editor { Editor putBoolean(String k,boolean v); Editor putInt(String k,int v); void apply(); boolean commit(); } }''')
w('android/content/ActivityNotFoundException.java','package android.content; public class ActivityNotFoundException extends RuntimeException {}')
w('android/content/pm/ActivityInfo.java','package android.content.pm; public class ActivityInfo { public static final int SCREEN_ORIENTATION_SENSOR_LANDSCAPE=6; }')
w('android/net/Uri.java','package android.net; public class Uri { public static Uri parse(String s){return new Uri();} }')
w('android/media/AudioManager.java','package android.media; public class AudioManager {public static final int STREAM_MUSIC=3;}')
w('android/app/Activity.java','''package android.app; import android.content.*;import android.os.Bundle;import android.view.*;
public class Activity extends Context { public static final int RESULT_OK=-1; public View root; public Intent result; public boolean finished=false;
protected void onCreate(Bundle b){} protected void onDestroy(){} public void onWindowFocusChanged(boolean b){}
public void addContentView(View v,ViewGroup.LayoutParams p){root=v;} public Application getApplication(){return new Application();}
public java.io.File getFilesDir(){return new java.io.File("/tmp");} public void setVolumeControlStream(int i){} public void setRequestedOrientation(int i){}
public boolean isFinishing(){return finished;} public void runOnUiThread(Runnable r){r.run();} public void setResult(int n,Intent i){result=i;}
public void finish(){finished=true;} public void startActivity(Intent i){} }
''')
w('android/widget/Toast.java','''package android.widget; import android.content.Context; public class Toast { public static final int LENGTH_LONG=1,LENGTH_SHORT=0; public static Toast makeText(Context c,CharSequence s,int x){return new Toast();} public void show(){} }''')
w('android/widget/FrameLayout.java','''package android.widget; import android.content.Context;import android.view.ViewGroup;public class FrameLayout extends ViewGroup {public FrameLayout(Context c){super(c);}public static class LayoutParams extends ViewGroup.LayoutParams {public int gravity,bottomMargin;public LayoutParams(int w,int h,int g){super(w,h);gravity=g;}}}''')
w('android/widget/LinearLayout.java','''package android.widget; import android.content.Context;import android.view.ViewGroup;public class LinearLayout extends ViewGroup {public static final int HORIZONTAL=0,VERTICAL=1;public LinearLayout(Context c){super(c);}public void setOrientation(int x){}public static class LayoutParams extends ViewGroup.LayoutParams { public LayoutParams(int w,int h){super(w,h);} public void setMarginStart(int s){} }}''')
p=r/'android/view/Gravity.java';s=p.read_text().replace('CENTER=17,','CENTER=17,CENTER_HORIZONTAL=1,');p.write_text(s)
p=r/'android/view/View.java';s=p.read_text().replace('public void setClickable(boolean v){}','public void setClickable(boolean v){} public void setContentDescription(CharSequence s){}');p.write_text(s)
p=r/'android/util/Log.java';s=p.read_text().replace('public static int v(', 'public static int e(String a,String b){return 0;} public static int v(');p.write_text(s)
w('org/libsdl/app2012/SDLActivity.java','''package org.libsdl.app2012; public class SDLActivity extends android.app.Activity {
public boolean useOpenTouch(){return true;}public String[] getLibraries(){return null;}public void setOrientationBis(int w,int h,boolean r,String s){}public void runLauncherAfterSdlSetup(String[] a){}public String getMainSharedObject(){return null;}public String[] getArguments(){return null;}}
''')
w('com/msa/freedoom/Mocks.java','''package com.msa.freedoom; class FnafOptionsActivity extends android.app.Activity {} class MenuCrashReporter {public static void install(android.content.Context x){}} class AppSettings {public static void reloadSettings(android.app.Application x){}} class Utils {public static void setImmersionMode(android.app.Activity x){} public static void onWindowFocusChanged(android.app.Activity x,boolean b){}}''')
PY
# Compile fakes used at runtime separately. The actual activity is compiled
# against the real Android API so its method descriptors match the device.
mkdir -p "$T/fakes" "$T/classes"
find "$T/src" -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -source 11 -target 11 -d "$T/fakes"
javac -encoding UTF-8 -source 11 -target 11 -cp "$ANDROID_JAR" -d "$T/classes" \
 "$T/src/org/libsdl/app2012/SDLActivity.java" "$T/src/com/msa/freedoom/Mocks.java" "$R/doom/src/main/java/com/msa/freedoom/FnafLaunchProfile.java"
"$KOTLINC" -jvm-target 11 -classpath "$ANDROID_JAR:$T/classes" \
 "$R/doom/src/main/java/com/msa/freedoom/FnafMenuSelection.kt" "$R/doom/src/main/java/com/msa/freedoom/OriginalMenuActivity.kt" -d "$T/classes"
cat > "$T/src/com/msa/freedoom/MenuButtonTest.java" <<'EOF'
package com.msa.freedoom;
import android.widget.*;
import android.view.*;
public class MenuButtonTest {
 static void eq(Object x,Object y){if(!x.equals(y)) throw new AssertionError(x+" != "+y);}
 static TextView button(LinearLayout v,int i){return (TextView)v.children.get(i);}
 public static void main(String[] args) throws Exception {
  var activity=new OriginalMenuActivity();
  var onCreate=OriginalMenuActivity.class.getDeclaredMethod("onCreate",android.os.Bundle.class);onCreate.setAccessible(true);onCreate.invoke(activity,new Object[]{null});
  var v=(LinearLayout)activity.root; eq(v.children.size(),5);
  eq(button(v,0).getText().toString(),"480p");eq(button(v,2).getText().toString(),"DIŞ: SİYAH");
  eq(button(v,3).getText().toString(),"DOKU: FPS");eq(button(v,4).getText().toString(),"KAMERA: NORMAL");
  button(v,0).click();button(v,2).click();button(v,3).click();button(v,4).click();
  eq(button(v,0).getText().toString(),"720p");eq(button(v,2).getText().toString(),"DIŞ: NORMAL");
  eq(button(v,3).getText().toString(),"DOKU: NORMAL");eq(button(v,4).getText().toString(),"KAMERA: TASARRUF");
  activity.onSingleplayerRequested("NIGHT1",0,0,0,0,0,0);
  var x=FnafMenuSelection.Companion.fromIntent(activity.result);
  if(!activity.finished || x==null || x.getResolution()!=720 || x.getOutsideBlack() || x.getLightTextures() || !x.getCameraSaver()) throw new AssertionError("wrong launch result");
  var again=new OriginalMenuActivity();onCreate.invoke(again,new Object[]{null});var u=(LinearLayout)again.root;
  eq(button(u,0).getText().toString(),"720p");eq(button(u,2).getText().toString(),"DIŞ: NORMAL");
  eq(button(u,3).getText().toString(),"DOKU: NORMAL");eq(button(u,4).getText().toString(),"KAMERA: TASARRUF");
  button(u,3).click();button(u,4).click();eq(button(u,3).getText().toString(),"DOKU: FPS");eq(button(u,4).getText().toString(),"KAMERA: NORMAL");
  System.out.println("PASS original-menu option taps, persistence, reversible toggles and game Intent");
 }
}
EOF
javac -encoding UTF-8 -source 11 -target 11 -cp "$T/fakes:$T/classes:$(dirname "$KOTLINC")/../lib/kotlin-stdlib.jar" -d "$T/fakes" "$T/src/com/msa/freedoom/MenuButtonTest.java"
java -cp "$T/fakes:$T/classes:$(dirname "$KOTLINC")/../lib/kotlin-stdlib.jar" com.msa.freedoom.MenuButtonTest
