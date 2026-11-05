#!/usr/bin/env python3
"""Exercise the actual SDL USB registration method under Android 14-style rules.

This is a JVM simulation of the platform contract, NOT a phone/emulator test.
Run --expect-legacy-crash on the unpatched source to demonstrate the bug.
"""
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
src = (ROOT/'doom/src/main/java/org/libsdl/app2012/HIDDeviceManager.java').read_text()
start = src.index('    private void initializeUSB() {')
end = src.index('    UsbManager getUSBManager()', start)
method = src[start:end].replace('android.os.Build.VERSION.SDK_INT', 'Build.VERSION.SDK_INT')\
                       .replace('android.os.Build.VERSION_CODES.TIRAMISU', 'Build.VERSION_CODES.TIRAMISU')
legacy = '--expect-legacy-crash' in sys.argv
java = r'''
import java.util.*;
public class HidReceiverHost {
 static class Build {static class VERSION {static int SDK_INT;} static class VERSION_CODES {static final int TIRAMISU=33;}}
 static class UsbDevice {}
 static class UsbManager {
  static final String ACTION_USB_DEVICE_ATTACHED="android.hardware.usb.action.USB_DEVICE_ATTACHED";
  static final String ACTION_USB_DEVICE_DETACHED="android.hardware.usb.action.USB_DEVICE_DETACHED";
  Map<String,UsbDevice> getDeviceList() {return Collections.singletonMap("device",new UsbDevice());}
 }
 static class IntentFilter {
  List<String> actions=new ArrayList<>();void addAction(String a){actions.add(a);}
 }
 static class Context {
  static final String USB_SERVICE="usb";
  static final int RECEIVER_EXPORTED=2, RECEIVER_NOT_EXPORTED=4;
  int targetSdk=36, registeredFlag=-7, calls=0; IntentFilter registeredFilter;
  Object getSystemService(String s) {return new UsbManager();}
  Object registerReceiver(Object r,IntentFilter f) {
   if (Build.VERSION.SDK_INT >= 34 && targetSdk >= 34 && f.actions.contains("org.libsdl.app.USB_PERMISSION"))
    throw new SecurityException("One of RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED should be specified");
   calls++;registeredFilter=f;registeredFlag=0;return null;
  }
  Object registerReceiver(Object r,IntentFilter f,int flags) {
   if (flags!=RECEIVER_EXPORTED && flags!=RECEIVER_NOT_EXPORTED) throw new IllegalArgumentException();
   calls++;registeredFilter=f;registeredFlag=flags;return null;
  }
 }
 static class HIDDeviceManager {
  static final String ACTION_USB_PERMISSION="org.libsdl.app.USB_PERMISSION";
  Context mContext=new Context();Object mUsbBroadcast=new Object();UsbManager mUsbManager;int attached=0;
  void handleUsbDeviceAttached(UsbDevice device){attached++;}
METHOD
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static HIDDeviceManager run(int sdk,boolean shouldFail) {
  Build.VERSION.SDK_INT=sdk; HIDDeviceManager m=new HIDDeviceManager();boolean failed=false;
  try {m.initializeUSB();} catch (SecurityException ex) {failed=true;}
  check(failed==shouldFail,"SDK "+sdk+" unexpected SecurityException="+failed);
  if (!failed) {
   check(m.attached==1,"device enumeration retained");
   check(m.mContext.calls==1,"only one registration");
   check(m.mContext.registeredFilter.actions.equals(Arrays.asList(UsbManager.ACTION_USB_DEVICE_ATTACHED,
      UsbManager.ACTION_USB_DEVICE_DETACHED,HIDDeviceManager.ACTION_USB_PERMISSION)),"all actions retained");
  }
  return m;
 }
 public static void main(String[] args) {
  boolean legacy=args.length>0 && args[0].equals("legacy");
  HIDDeviceManager a=run(33,false); HIDDeviceManager b=run(34,legacy);
  if (!legacy) {
   check(a.mContext.registeredFlag==Context.RECEIVER_NOT_EXPORTED,"API33 private receiver");
   check(b.mContext.registeredFlag==Context.RECEIVER_NOT_EXPORTED,"API34 private receiver");
   check(run(35,false).mContext.registeredFlag==Context.RECEIVER_NOT_EXPORTED,"API35 private receiver");
  }
  System.out.println(legacy ? "PASS reproduced Android 14 registration crash on old source" :
   "PASS actual HID USB registration: API 33/34/35, private receiver, actions and enumeration");
 }
}
'''.replace('METHOD', method)
with tempfile.TemporaryDirectory() as d:
    p=Path(d)/'HidReceiverHost.java'; p.write_text(java)
    subprocess.run(['javac','-d',d,str(p)],check=True)
    subprocess.run(['java','-cp',d,'HidReceiverHost']+(['legacy'] if legacy else []),check=True)
