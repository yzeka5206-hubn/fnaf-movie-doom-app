package org.libsdl.app;
import android.app.Activity;
import android.os.Message;

public class KeyboardBridgeHostTest {
 public static void main(String[] args) {
  EventLog.clear();
  Message msg=new Message(); msg.arg1=0x8003;
  if (!SDLOpenTouch.CommandHandler(new Activity(),msg)) throw new AssertionError("Native keyboard message was not handled");
  if (EventLog.events.size()!=1 || !EventLog.events.get(0).equals("KEYBOARD_VIEW"))
   throw new AssertionError("Native keyboard callback did not show SDL editor: "+EventLog.events);
  msg.arg1=42;
  if (SDLOpenTouch.CommandHandler(new Activity(),msg)) throw new AssertionError("Unrelated message unexpectedly consumed");
  System.out.println("PASS native system-keyboard message opens Android SDL editor");
 }
}
