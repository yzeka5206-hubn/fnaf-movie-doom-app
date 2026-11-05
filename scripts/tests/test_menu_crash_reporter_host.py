#!/usr/bin/env python3
"""Run the real menu-only Java crash recorder against Android JVM fakes.
This verifies handling logic, not Android's actual process lifecycle.
"""
import subprocess
import tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
src=ROOT/'doom/src/main/java/com/msa/freedoom/MenuCrashReporter.java'
files={
'android/content/Context.java': '''package android.content;
import java.io.File; import android.app.ActivityManager;
public class Context {
 public final File files; public final ActivityManager manager=new ActivityManager();
 public Context(File f){files=f;} public File getFilesDir(){return files;}
 public Context getApplicationContext(){return this;} public String getPackageName(){return "com.fnaf.moviedoom.menutest";}
 public <T>T getSystemService(Class<T> c){return c.cast(manager);}
}''',
'android/app/ApplicationExitInfo.java': '''package android.app;
public class ApplicationExitInfo {
 public static final int REASON_CRASH=4,REASON_CRASH_NATIVE=5,REASON_ANR=6,REASON_INITIALIZATION_FAILURE=7,REASON_LOW_MEMORY=3;
 public final String name,description;public final long timestamp;public final int reason,status;
 public ApplicationExitInfo(String n,long t,int r,int s,String d){name=n;timestamp=t;reason=r;status=s;description=d;}
 public String getProcessName(){return name;}public long getTimestamp(){return timestamp;}
 public int getReason(){return reason;}public int getStatus(){return status;}public String getDescription(){return description;}
}''',
'android/app/ActivityManager.java': '''package android.app;
import java.util.*; public class ActivityManager {
 public List<ApplicationExitInfo> exits=new ArrayList<>();
 public List<ApplicationExitInfo> getHistoricalProcessExitReasons(String pkg,int pid,int max){return exits;}
}''',
'android/os/Process.java': '''package android.os;public class Process {public static int myPid(){return 1;}public static void killProcess(int p){throw new AssertionError("unexpected kill");}}''',
'com/msa/freedoom/MenuCrashHost.java': '''package com.msa.freedoom;
import android.content.Context;import android.app.ApplicationExitInfo;import java.io.*;import java.util.concurrent.atomic.AtomicInteger;
public class MenuCrashHost {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args) throws Exception {
  Context c=new Context(new File(args[0])); long launched=System.currentTimeMillis();
  check(MenuCrashReporter.diagnose(c,launched)==null,"no report initially");
  AtomicInteger previousCalls=new AtomicInteger();
  Thread.setDefaultUncaughtExceptionHandler((t,e)->previousCalls.incrementAndGet());
  MenuCrashReporter.install(c); MenuCrashReporter.install(c);
  RuntimeException outer=new RuntimeException("outer",new SecurityException("USB receiver rejected"));
  Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(),outer);
  String s=MenuCrashReporter.diagnose(c,launched);
  check(s!=null && s.contains("Asıl neden: java.lang.SecurityException: USB receiver rejected") && s.contains("outer"),"Java stack recorded");
  check(previousCalls.get()==1,"prior Android crash handler invoked exactly once");
  MenuCrashReporter.clear(c); check(MenuCrashReporter.diagnose(c,launched)==null,"old report removed");
  String pkg="com.fnaf.moviedoom.menutest:OriginalMenu";
  c.manager.exits.add(new ApplicationExitInfo("different:Game",launched,5,11,"wrong process"));
  c.manager.exits.add(new ApplicationExitInfo(pkg,launched-3000,5,11,"old crash"));
  c.manager.exits.add(new ApplicationExitInfo(pkg,launched,2,9,"intentional shutdown"));
  check(MenuCrashReporter.diagnose(c,launched)==null,"different, old, intentional exits ignored");
  c.manager.exits.add(new ApplicationExitInfo(pkg,launched,5,11,"SIGSEGV"));
  String nativeExit=MenuCrashReporter.diagnose(c,launched);
  check(nativeExit!=null && nativeExit.contains("nedeni: 5") && nativeExit.contains("SIGSEGV"),"recent native exit surfaced");
  System.out.println("PASS menu crash recorder: real Java handler, cause, old-handler chaining and exit filtering");
 }
}'''
}
with tempfile.TemporaryDirectory() as d:
 p=Path(d)
 for name,content in files.items():
  f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(content,encoding='utf-8')
 classes=p/'classes';classes.mkdir()
 subprocess.run(['javac','-encoding','UTF-8','-d',str(classes),str(src)]+[str(p/name) for name in files],check=True)
 output=p/'data';output.mkdir()
 subprocess.run(['java','-cp',str(classes),'com.msa.freedoom.MenuCrashHost',str(output)],check=True)
