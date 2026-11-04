package org.libsdl.app2012;
import android.os.Handler;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import org.libsdl.app.EventLog;
import java.util.Arrays;

public class SoftKeyboardHostTest {
 static void check(boolean b,String message) {if(!b)throw new AssertionError(message+" "+EventLog.events);}
 static void count(int n){check(EventLog.events.size()==n,"Expected event count "+n);}
 public static void main(String[] args) {
  SDLInputConnection input=new SDLInputConnection(new View(new android.content.Context()),true);
  EventLog.clear();
  input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));
  input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));
  check(EventLog.events.equals(Arrays.asList("KD:66","KU:66")),"Soft Enter must forward one real Return");
  input.performEditorAction(6); count(2); // same IME action reported twice
  Handler.advance(181); input.performEditorAction(6); count(4);
  Handler.advance(181); input.commitText("\n",1); count(6);
  Handler.advance(181); input.commitText("\r\n",1); count(8); // CRLF is a single Return
  check(EventLog.events.equals(Arrays.asList("KD:66","KU:66","KD:66","KU:66","KD:66","KU:66","KD:66","KU:66")),"IME event order");
  System.out.println("PASS soft keyboard Enter/Done/newline/CRLF, duplicate suppression");
 }
}
