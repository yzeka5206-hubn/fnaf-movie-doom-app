#!/usr/bin/env python3
"""Run the real SDL menu touch conversion block on JVM fakes; not a phone test."""
import subprocess
import tempfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
src = (ROOT/'doom/src/main/java/org/libsdl/app2012/SDLActivity.java').read_text()
fields = src[src.index('    private boolean menuMouseHeld = false;'):src.index('    // Touch events\n')]
start = src.index('        if (!SDLActivity.OPENTOUCH_SDL_EXTRA &&\n', src.index('    // Touch events\n'))
end = src.index('        // 12290 = Samsung DeX', start)
block = src[start:end].replace('android.os.SystemClock.uptimeMillis()', 'now')
destroy = src[src.index('        removeCallbacks(menuMouseRelease);', src.index('public void surfaceDestroyed')):
              src.index('        // Transition to pause', src.index('public void surfaceDestroyed'))]
java = r'''
import java.util.*;
public class MenuTouchHost {
 static final class InputDevice {static final int SOURCE_TOUCHSCREEN=4098;}
 static final class MotionEvent {
  static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;
  int action;float x,y;MotionEvent(int a,float px,float py){action=a;x=px;y=py;}
  int getSource(){return InputDevice.SOURCE_TOUCHSCREEN;}
  float getX(int i){return x;} float getY(int i){return y;}
 }
 static final class SDLActivity {
  static boolean OPENTOUCH_SDL_EXTRA=false; static List<String> events=new ArrayList<>();
  static void onNativeMouse(int b,int a,float x,float y,boolean relative){events.add(b+":"+a+":"+(int)x+":"+(int)y+":"+relative);}
 }
 static long now;
 Runnable queued; long due;
 boolean postDelayed(Runnable r,long delay){queued=r;due=now+delay;return true;}
 boolean removeCallbacks(Runnable r){if(queued==r)queued=null;return true;}
 void advance(long millis){now+=millis;if(queued!=null&&due<=now){Runnable task=queued;queued=null;task.run();}}
 FIELDS
 boolean touch(int action,float x,float y){MotionEvent event=new MotionEvent(action,x,y);BLOCK return false;}
 void destroy(){DESTROY}
 static void check(boolean b,String s){if(!b)throw new AssertionError(s+" "+SDLActivity.events);}
 public static void main(String[] args){
  MenuTouchHost t=new MenuTouchHost();now=0;
  check(t.touch(0,200,300),"down accepted");
  check(SDLActivity.events.equals(Arrays.asList("1:0:200:300:false")),"absolute left down");
  t.advance(10);t.touch(1,201,301);t.advance(89);check(SDLActivity.events.size()==1,"click held for 100ms");
  t.advance(1);check(SDLActivity.events.get(1).equals("0:1:201:301:false"),"release sent once at end position");
  t.touch(0,400,500);t.touch(2,410,510);t.advance(200);t.touch(1,410,510);t.advance(0);
  check(SDLActivity.events.subList(2,5).equals(Arrays.asList("1:0:400:500:false","1:2:410:510:false","0:1:410:510:false")),"drag and long release");
  t.touch(0,10,20);t.touch(1,10,20);t.destroy();t.advance(1000);
  check(SDLActivity.events.subList(5,7).equals(Arrays.asList("1:0:10:20:false","0:1:10:20:false")),"destroy releases and cancels callbacks");
  check(SDLActivity.events.size()==7,"no duplicate release");
  SDLActivity.OPENTOUCH_SDL_EXTRA=true;check(!t.touch(0,10,20),"game uses separate path");
  System.out.println("PASS actual menu touch-to-absolute-mouse block: short/long tap, drag, delayed release and surface destruction");
 }
}
'''.replace('FIELDS',fields).replace('BLOCK',block).replace('DESTROY',destroy)
with tempfile.TemporaryDirectory() as d:
    p=Path(d)/'MenuTouchHost.java';p.write_text(java)
    subprocess.run(['javac','-d',d,str(p)],check=True)
    subprocess.run(['java','-cp',d,'MenuTouchHost'],check=True)
