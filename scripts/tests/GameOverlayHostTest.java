package com.msa.freedoom;

import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.libsdl.app.EventLog;
import org.libsdl.app.SDLOpenTouch;

public class GameOverlayHostTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message + " | events=" + EventLog.events);
    }
    private static boolean has(String s) { return EventLog.events.contains(s); }
    private static TextView find(View v, String text) {
        if (v.getVisibility() != View.VISIBLE) return null;
        if (v instanceof TextView && ((TextView)v).getText().toString().equals(text)) return (TextView)v;
        if (v instanceof ViewGroup) for (View child : ((ViewGroup)v).children) {
            TextView result = find(child, text); if (result != null) return result;
        }
        return null;
    }
    private static TextView button(Game g, String text) {
        TextView result = find(g.root, text); check(result != null, "Missing button " + text); return result;
    }
    private static void touch(View view, int action, float x, float y) {
        view.dispatchTouchEvent(new MotionEvent(action, x, y));
    }
    private static void down(View view) { touch(view, MotionEvent.ACTION_DOWN, 10, 10); }
    private static void up(View view) { touch(view, MotionEvent.ACTION_UP, 10, 10); }
    private static void tap(View view) { down(view); up(view); }
    private static void mode(Game game, int state) throws Exception { mode(game, state, false); }
    private static void mode(Game game, int state, boolean pauseMenu) throws Exception {
        Method method = Game.class.getDeclaredMethod("setGameUiMode", int.class, FnafBackBounds.class, boolean.class);
        method.setAccessible(true);
        method.invoke(game, state, state == 2 ? new FnafBackBounds(0, 0, 48, 39, 570, 570) : null, pauseMenu);
    }
    private static void ack(Game game, int serial, boolean accepted) throws Exception {
        Method method = Game.class.getDeclaredMethod("handlePauseTapAck", FnafPauseTapAck.class);
        method.setAccessible(true);
        method.invoke(game, new FnafPauseTapAck(serial, accepted));
    }
    private static void margin(View v, int right, int bottom) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)v.getLayoutParams();
        check(lp.rightMargin == right * 3 && lp.bottomMargin == bottom * 3, "wrong FENER/EGIL position");
    }
    public static void main(String[] args) throws Exception {
        Handler.clear(); SDLOpenTouch.ready = true;
        Game g = new Game(); g.onCreate(null);
        TextView fener = button(g, "FENER"), kos = button(g, "KOS"), egil = button(g, "EGIL");
        TextView saat = button(g, "SAAT"), keyboard = button(g, "KLAVYE"), al = button(g, "AL");
        check(find(g.root, "FARE") == null && find(g.root, "TIK") == null, "old mouse buttons must not exist");
        margin(fener, 122, 128); margin(egil, 132, 36);

        EventLog.clear(); tap(al);
        check(has("KD:33") && has("KU:33") && !has("A:1:13"), "AL must send E, not attack");
        EventLog.clear(); tap(kos);
        check(has("A:1:10") && has("KD:59") && !has("A:0:10") && !has("KU:59"), "run stays enabled after release");
        check(kos.getText().toString().contains("ACIK"), "run indicator");
        tap(kos); check(has("A:0:10") && has("KU:59"), "second tap stops run");
        EventLog.clear(); tap(saat); check(has("A:1:64") && !has("A:0:64"), "watch latch");
        tap(saat); check(has("A:0:64"), "watch release");
        EventLog.clear(); tap(egil); check(has("A:1:15") && !has("A:0:15"), "crouch latch");
        tap(egil); check(has("A:0:15"), "crouch release");
        EventLog.clear(); tap(fener); check(has("A:1:13"), "flashlight pulse on");
        Handler.advance(150); check(has("A:0:13"), "flashlight pulse off");

        ViewGroup root = (ViewGroup)g.root; View look = root.children.get(0), stick = root.children.get(1);
        EventLog.clear(); touch(stick, MotionEvent.ACTION_DOWN, 200, 200); touch(stick, MotionEvent.ACTION_MOVE, 200, 20); touch(stick, MotionEvent.ACTION_UP, 200, 20);
        check(has("KD:51") && has("KU:51"), "working WASD walk remains");
        EventLog.clear(); touch(look, MotionEvent.ACTION_DOWN, 100, 100); touch(look, MotionEvent.ACTION_MOVE, 130, 140); touch(look, MotionEvent.ACTION_UP, 130, 140);
        check(has("M:0:2:-30.0:-40.0:true"), "camera axes remain corrected");
        check(EventLog.events.stream().anyMatch(x -> x.startsWith("LOOK:-0.063") && x.endsWith(":-0.0165")), "analog look remains corrected");

        EventLog.clear(); tap(kos); tap(fener); mode(g, 1);
        check(has("A:0:10") && has("KU:59") && has("A:0:13"), "menu releases run and flashlight");
        check(al.getVisibility() == View.GONE && stick.getVisibility() == View.GONE, "selector exposes touch area");
        EventLog.clear(); touch(look, MotionEvent.ACTION_DOWN, 285, 427.5f); touch(look, MotionEvent.ACTION_UP, 285, 427.5f);
        check(has("CMD:interfaceevent FnafTouch 32768 49151 0") && has("CMD:interfaceevent FnafTouch 32768 49151 2"), "character tap coordinates");
        check(!has("A:1:13") && EventLog.events.stream().noneMatch(x -> x.startsWith("M:1:")), "tap must not toggle flashlight or inject Mouse1");
        EventLog.clear(); touch(look, MotionEvent.ACTION_DOWN, -10, 9999); touch(look, MotionEvent.ACTION_CANCEL, -10, 9999);
        check(has("CMD:interfaceevent FnafTouch 0 65535 0") && has("CMD:interfaceevent FnafTouch 0 65535 3"), "coordinate clamp and cancel");
        // The original pause list uses a session/serial-acknowledged bridge.
        // Its GUI key is not emitted until the engine has armed that exact tap.
        mode(g, 2, true); EventLog.clear();
        Field sessionField = Game.class.getDeclaredField("uiSession"); sessionField.setAccessible(true);
        int session = sessionField.getInt(g);
        touch(look, MotionEvent.ACTION_DOWN, 285, 285); touch(look, MotionEvent.ACTION_UP, 285, 285);
        check(has("CMD:interfaceevent FnafPauseTap " + session + " " + ((32768 << 16) | 32768) + " 1") && !has("KD:142"), "pause tap queues session and normalized coordinates");
        ack(g, 99, true); check(!has("KD:142"), "stale acknowledgement ignored");
        ack(g, 1, true); check(has("KD:142") && !has("M:1:0:285.0:285.0:false"), "matching ack sends protected UI key");
        Handler.advance(100); check(has("KU:142"), "protected UI key released");
        EventLog.clear(); touch(look, MotionEvent.ACTION_DOWN, 285, 285); touch(look, MotionEvent.ACTION_UP, 285, 285);
        tap(button(g, "GERI"));
        check(has("CMD:interfaceevent FnafPauseCancel " + session + " 2") && has("MENU"), "back cancels pending tap");
        ack(g, 2, true); check(!has("KD:142"), "cancelled tap cannot activate later");
        EventLog.clear(); touch(look, MotionEvent.ACTION_DOWN, 285, 285); touch(look, MotionEvent.ACTION_UP, 285, 285);
        Handler.advance(1200);
        check(has("CMD:interfaceevent FnafPauseCancel " + session + " 3") && has("M:1:0:285.0:285.0:false"), "missing ack falls back to SDL");
        Handler.advance(100); check(has("M:0:1:285.0:285.0:false"), "fallback releases SDL mouse");

        // Other engine menus: horizontal drags and taps use actual SDL mouse
        // input, never an unsafe direct call into menu methods.
        mode(g, 2, false);
        check(button(g, "GERI") != null && button(g, "ENTER") != null &&
                button(g, "▲") != null && button(g, "▼") != null &&
                button(g, "◀") != null && button(g, "▶") != null,
                "menu has a reliable back and keyboard navigation fallback");
        EventLog.clear(); tap(button(g, "GERI"));
        check(has("MENU"), "menu fallback goes through native back/escape");
        EventLog.clear(); tap(button(g, "▲")); tap(button(g, "▼"));
        tap(button(g, "◀")); tap(button(g, "▶")); tap(button(g, "ENTER"));
        check(has("KD:19") && has("KU:19") && has("KD:20") && has("KU:20") &&
                has("KD:21") && has("KU:21") && has("KD:22") && has("KU:22") &&
                has("KD:66") && has("KU:66"), "fallback navigation uses SDL key events");
        EventLog.clear();
        touch(look, MotionEvent.ACTION_DOWN, 285, 285); Handler.advance(40);
        touch(look, MotionEvent.ACTION_MOVE, 375, 295); touch(look, MotionEvent.ACTION_UP, 375, 295);
        check(has("M:1:0:285.0:285.0:false") && has("M:1:2:375.0:295.0:false"), "SDL slider drag");
        check(EventLog.events.stream().noneMatch(x -> x.startsWith("CMD:interfaceevent FnafTouch ")), "no direct menu adapter calls");
        check(!has("M:0:1:375.0:295.0:false"), "keep mouse down until the engine sees it");
        Handler.advance(100); check(has("M:0:1:375.0:295.0:false"), "release slider");

        EventLog.clear(); touch(look, MotionEvent.ACTION_DOWN, 285, 285);
        check(EventLog.events.isEmpty(), "defer down to distinguish scroll from a tap");
        touch(look, MotionEvent.ACTION_UP, 285, 285);
        check(has("M:1:0:285.0:285.0:false"), "SDL tap presses once");
        Handler.advance(99); check(!has("M:0:1:285.0:285.0:false"), "short tap not released too soon");
        Handler.advance(1); check(has("M:0:1:285.0:285.0:false"), "short tap releases");

        EventLog.clear(); touch(look, MotionEvent.ACTION_DOWN, 20, 20); touch(look, MotionEvent.ACTION_UP, 20, 20);
        check(has("M:16:0:20.0:20.0:false") && !has("M:1:0:20.0:20.0:false"), "arrow uses SDL back button, not broken left-click path");
        Handler.advance(100); check(has("M:0:1:20.0:20.0:false"), "SDL back button released");
        EventLog.clear(); touch(look, MotionEvent.ACTION_DOWN, 20, 20); touch(look, MotionEvent.ACTION_MOVE, 80, 20); touch(look, MotionEvent.ACTION_UP, 80, 20);
        check(EventLog.events.isEmpty(), "dragging out of arrow cancels back");

        EventLog.clear(); touch(look, MotionEvent.ACTION_DOWN, 200, 300);
        touch(look, MotionEvent.ACTION_MOVE, 200, 180); touch(look, MotionEvent.ACTION_UP, 200, 180);
        check(has("M:0:8:0.0:-1.0:false") && EventLog.events.stream().noneMatch(x -> x.startsWith("M:1:0:")), "vertical swipe scrolls without selecting a row");

        SDLOpenTouch.sw = 285; SDLOpenTouch.sh = 190;
        EventLog.clear(); touch(look, MotionEvent.ACTION_DOWN, 285, 285); touch(look, MotionEvent.ACTION_UP, 285, 285);
        check(has("M:1:0:142.5:95.0:false"), "coordinates scale to the SDL surface when res_div differs");
        mode(g, 0);
        check(has("M:0:1:142.5:95.0:false") && EventLog.events.stream().anyMatch(x -> x.startsWith("CMD:interfaceevent FnafTouchAck ")), "mode change releases mouse and acknowledges gameplay");
        check(al.getVisibility() == View.VISIBLE && button(g, "MENU") != null &&
                find(g.root, "GERI") == null && find(g.root, "◀") == null && find(g.root, "▶") == null,
                "controls return on gameplay and menu-only arrows disappear");
        Handler.advance(200); check(EventLog.events.stream().filter(x -> x.equals("M:0:1:142.5:95.0:false")).count() == 1, "no stale duplicate release");
        SDLOpenTouch.sw = SDLOpenTouch.sh = 570;
        EventLog.clear(); touch(look, MotionEvent.ACTION_DOWN, 285, 285); touch(look, MotionEvent.ACTION_UP, 285, 285);
        check(has("CMD:interfaceevent FnafTouch 32768 32768 0") && !has("A:1:13"), "late-state tap is guarded");

        EventLog.clear(); tap(keyboard);
        check(keyboard.getText().toString().contains("ACIK") && al.getVisibility() == View.GONE, "keyboard opens and hides controls");
        button(g, "a").click(); check(has("KD:29") && has("KU:29") && has("TEXT:a"), "ASCII key and SDL text");
        EventLog.clear(); button(g, "ğ").click(); check(has("TEXT:ğ"), "Turkish character");
        EventLog.clear(); button(g, "⇧").click(); button(g, "İ").click(); check(has("TEXT:İ"), "Turkish uppercase");
        EventLog.clear(); button(g, "⌫").click(); button(g, "←").click(); button(g, "ENTER").click();
        check(has("KD:67") && has("KU:67") && has("KD:21") && has("KD:66"), "backspace, arrow, enter");
        button(g, "?123").click(); EventLog.clear(); button(g, "@").click(); check(has("TEXT:@"), "symbols");
        tap(keyboard); check(keyboard.getText().toString().equals("KLAVYE") && al.getVisibility() == View.VISIBLE, "same key closes keyboard");
        EventLog.clear(); tap(kos); tap(egil); tap(fener); g.onPause();
        check(has("A:0:10") && has("KU:59") && has("A:0:15") && has("A:0:13"), "pause releases latched inputs");
        System.out.println("PASS direct touch, swapped buttons, run latch, walking, embedded keyboard, and lifecycle releases");
    }
}
