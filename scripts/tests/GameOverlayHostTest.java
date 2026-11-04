package com.msa.freedoom;

import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.libsdl.app.EventLog;
import java.util.*;

public class GameOverlayHostTest {
    static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason + " | events=" + EventLog.events);
    }
    static void events(String... expected) {
        check(EventLog.events.equals(Arrays.asList(expected)), "Expected " + Arrays.toString(expected));
    }
    static TextView find(View root, String label) {
        if (root instanceof TextView && ((TextView)root).getText().toString().startsWith(label)) return (TextView)root;
        if (root instanceof ViewGroup) for (View v : ((ViewGroup)root).children) {
            TextView found=find(v,label); if(found!=null) return found;
        }
        return null;
    }
    static TextView button(Game g, String label) {
        TextView v=find(g.root,label); check(v!=null,"Missing button "+label); return v;
    }
    static void down(View v) { v.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,10,10)); }
    static void up(View v) { v.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP,10,10)); }
    static void tap(View v) { down(v); up(v); }
    static void clean() { EventLog.clear(); Handler.clear(); }
    public static void main(String[] args) {
        Game g = new Game(); g.onCreate(null);
        TextView al=button(g,"AL"), light=button(g,"FENER"), watch=button(g,"SAAT"),
                 crouch=button(g,"EGIL"), mouse=button(g,"FARE"), click=button(g,"TIK"),
                 kb=button(g,"KLAVYE"), menu=button(g,"MENU");
        ViewGroup root=(ViewGroup)g.root;
        View look=root.children.get(0), joystick=root.children.get(1);

        clean(); tap(al); events("KD:"+KeyEvent.KEYCODE_E,"KU:"+KeyEvent.KEYCODE_E);
        System.out.println("PASS AL sends only E");

        clean(); down(light); Handler.advance(149); up(light); events("A:1:13");
        Handler.advance(1); events("A:1:13","A:0:13"); Handler.advance(2000);
        events("A:1:13","A:0:13");
        System.out.println("PASS flashlight is one short press even when held");

        clean(); tap(watch); events("A:1:64"); check(watch.getText().toString().contains("ACIK"),"Watch indicator on");
        tap(watch); events("A:1:64","A:0:64"); check(!watch.getText().toString().contains("ACIK"),"Watch indicator off");
        tap(crouch); tap(crouch); events("A:1:64","A:0:64","A:1:15","A:0:15");
        System.out.println("PASS watch and crouch are tap-on/tap-off");

        clean(); look.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,100,100));
        look.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_MOVE,130,140));
        look.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP,130,140));
        check(EventLog.events.size()==2, "Expected SDL + analog camera events");
        check(EventLog.events.get(0).equals("M:0:2:-30.0:-40.0:true"), "Camera reverses both mouse axes");
        check(EventLog.events.get(1).startsWith("LOOK:-0.063"), "Camera reverses analog pitch");
        check(EventLog.events.get(1).endsWith(":-0.0165"), "Camera reverses analog yaw");
        System.out.println("PASS both camera directions reversed on both input paths");

        clean(); tap(watch); tap(crouch); EventLog.clear(); tap(mouse);
        check(EventLog.events.contains("A:0:64") && EventLog.events.contains("A:0:15"),"Mouse mode releases both latches");
        check(al.getVisibility()==View.GONE && joystick.getVisibility()==View.GONE && click.getVisibility()==View.VISIBLE,"Mouse mode visibility");
        check(EventLog.events.stream().noneMatch(e -> e.equals("MENU") || e.startsWith("KD:")),"Mouse mode should not send escape or enter");
        clean(); look.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,100,100));
        look.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_MOVE,130,140));
        look.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP,130,140));
        events("M:0:2:30.0:40.0:true");
        clean(); tap(click); events("M:1:0:0.0:0.0:true","M:0:1:0.0:0.0:true");
        clean(); down(click); tap(mouse);
        events("M:1:0:0.0:0.0:true","M:0:1:0.0:0.0:true");
        check(click.getVisibility()==View.GONE && al.getVisibility()==View.VISIBLE,"Gameplay restored when mouse closes");
        System.out.println("PASS relative cursor and real SDL left click; no duplicate analog look or ESC");

        clean(); tap(kb); events("A:1:26"); Handler.advance(700);
        events("A:1:26","KEYBOARD_VIEW");
        System.out.println("PASS keyboard requests SDL text input and fallback editor");

        clean(); joystick.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,200,200));
        joystick.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_MOVE,200,20));
        joystick.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP,200,20));
        events("KD:"+KeyEvent.KEYCODE_W,"KU:"+KeyEvent.KEYCODE_W);
        System.out.println("PASS working WASD movement remains unchanged");

        clean(); tap(watch); tap(crouch); down(al); EventLog.clear(); g.onPause();
        check(EventLog.events.contains("A:0:64") && EventLog.events.contains("A:0:15") && EventLog.events.contains("KU:"+KeyEvent.KEYCODE_E),"Pause releases held inputs");
        check(!watch.getText().toString().contains("ACIK") && !crouch.getText().toString().contains("ACIK"),"Pause resets latch indicators");
        EventLog.clear(); up(al); events();
        System.out.println("PASS pause releases latched and held inputs without double release");

        clean(); tap(watch); EventLog.clear(); tap(menu); events("A:0:64","MENU");
        System.out.println("PASS explicit MENU releases inputs before opening menu");
        System.out.println("ALL GAME OVERLAY HOST TESTS PASSED");
    }
}
