package com.fnaf.moviedoom.repair;
import java.io.*;import java.util.*;import java.security.*;
public class Test {
 static byte[] hash(byte[] b)throws Exception{return MessageDigest.getInstance("SHA-256").digest(b);}
 static void copy(DataOutputStream d,long o,long n)throws Exception {d.writeByte(0);d.writeLong(o);d.writeLong(n);}
 static void lit(DataOutputStream d,String s)throws Exception {byte[] b=s.getBytes("UTF-8");d.writeByte(1);d.writeLong(b.length);d.write(b);}
 static byte[] patch(byte[] old,byte[] neu,boolean wrongOutput,boolean reverse)throws Exception {
  ByteArrayOutputStream out=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(out);
  d.writeBytes("FNAFPCH2");d.writeLong(old.length);d.writeLong(neu.length);d.write(hash(old));byte[] h=hash(neu);if(wrongOutput)h[0]^=1;d.write(h);d.writeInt(6);
  copy(d,0,1);lit(d,"XY");copy(d,3,3);lit(d,"ZW");copy(d,reverse?2:8,2);lit(d,"++++");return out.toByteArray();
 }
 static void rejects(byte[] s,byte[] p,String name)throws Exception {
  try{PatchEngine.apply(new ByteArrayInputStream(s),new ByteArrayInputStream(p),new ByteArrayOutputStream());throw new RuntimeException(name+" accepted");}
  catch(IOException expected){System.out.println("PASS rejects "+name);}
 }
 public static void main(String[] args)throws Exception {
  byte[] a="abcdefghij".getBytes("UTF-8"),b="aXYdefZWij++++".getBytes("UTF-8"),p=patch(a,b,false,false);
  ByteArrayOutputStream out=new ByteArrayOutputStream();PatchEngine.apply(new ByteArrayInputStream(a),new ByteArrayInputStream(p),out);if(!Arrays.equals(out.toByteArray(),b))throw new Exception("unequal output");System.out.println("PASS shifted, unequal size copy/literal delta");
  byte[] bad=a.clone();bad[4]^=1;rejects(bad,p,"wrong source");rejects(a,patch(a,b,true,false),"wrong target SHA");rejects(a,patch(a,b,false,true),"backward copy");rejects(Arrays.copyOf(a,9),p,"truncated source");rejects(a,Arrays.copyOf(p,p.length-1),"truncated patch");rejects(a,Arrays.copyOf(p,p.length+1),"trailing patch data");
  if(args.length==3){try(InputStream src=new FileInputStream(args[0]);InputStream pd=new FileInputStream(args[1]);OutputStream dst=new FileOutputStream(args[2])){PatchEngine.apply(src,pd,dst);}System.out.println("PASS actual APK delta");}
 }
}
