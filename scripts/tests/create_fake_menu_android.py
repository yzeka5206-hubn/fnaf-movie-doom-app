"""Small Android view fakes for testing the real FNAF menu on a normal JVM."""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('Usage: create_fake_menu_android.py <output-directory>')
r = Path(sys.argv[1])
files = {
'android/content/res/Resources.java': '''package android.content.res; public class Resources {}''',
'android/content/Context.java': '''package android.content; public class Context {private final android.content.res.Resources r=new android.content.res.Resources(); public android.content.res.Resources getResources(){return r;} }''',
'android/content/res/ColorStateList.java': '''package android.content.res; public class ColorStateList {public static ColorStateList valueOf(int c){return new ColorStateList();}}''',
'android/graphics/Bitmap.java': '''package android.graphics; public class Bitmap {}''',
'android/graphics/BitmapFactory.java': '''package android.graphics; import android.content.res.Resources; public class BitmapFactory {public static class Options {public boolean inScaled=true;} public static Bitmap decodeResource(Resources r,int i,Options o){if(o.inScaled)throw new AssertionError("Artwork must not be density scaled");return new Bitmap();}}''',
'android/graphics/Color.java': '''package android.graphics; public class Color {public static final int TRANSPARENT=0,WHITE=-1,BLACK=0xff000000;}''',
'android/graphics/Typeface.java': '''package android.graphics; public class Typeface {public static final int BOLD=1;public static Typeface create(String s,int x){return new Typeface();}}''',
'android/graphics/Paint.java': '''package android.graphics; public class Paint {public static final int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2;public Paint(int f){}public void setColor(int c){}public void setTypeface(Typeface t){}public void setTextSize(float s){} }''',
'android/graphics/Canvas.java': '''package android.graphics; import java.util.*; public class Canvas {public final List<String> text=new ArrayList<>();public int bitmapCount=0;public void drawColor(int c){} public int save(){return 1;} public void translate(float x,float y){} public void scale(float x,float y){} public void drawBitmap(Bitmap b,float x,float y,Paint p){bitmapCount++;} public void drawRect(float a,float b,float c,float d,Paint p){} public void drawText(String s,float x,float y,Paint p){text.add(s);} public void restore(){} }''',
'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable; public class Drawable {}''',
'android/graphics/drawable/ColorDrawable.java': '''package android.graphics.drawable; public class ColorDrawable extends Drawable {public ColorDrawable(int c){}}''',
'android/graphics/drawable/RippleDrawable.java': '''package android.graphics.drawable;import android.content.res.ColorStateList; public class RippleDrawable extends Drawable {public RippleDrawable(ColorStateList c,Drawable d,Drawable m){}}''',
'android/view/View.java': '''package android.view;
import android.content.Context;import android.content.res.Resources;import android.graphics.Canvas;import android.graphics.drawable.Drawable;
public class View {
 protected final Context context;private OnClickListener click;private boolean enabled=true;
 public int left,top,right,bottom,measuredWidth,measuredHeight;public CharSequence description;
 public View(Context c){context=c;}public Context getContext(){return context;}public Resources getResources(){return context.getResources();}
 public interface OnClickListener {void onClick(View v);}public void setOnClickListener(OnClickListener l){click=l;}
 public boolean performClick(){if(!enabled||click==null)return false;click.onClick(this);return true;}
 public void setEnabled(boolean e){enabled=e;} public boolean isEnabled(){return enabled;} public void invalidate(){}
 public void setContentDescription(CharSequence s){description=s;}public CharSequence getContentDescription(){return description;}
 public void setMinimumWidth(int x){}public void setMinimumHeight(int x){}public void setPadding(int a,int b,int c,int d){}public void setBackground(Drawable d){}
 public int getWidth(){return right-left;}public int getHeight(){return bottom-top;}
 public static class MeasureSpec {public static final int EXACTLY=0x40000000; public static int makeMeasureSpec(int size,int mode){return (size&0x3fffffff)|mode;}public static int getSize(int spec){return spec&0x3fffffff;} }
 public final void measure(int w,int h){onMeasure(w,h);}protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),MeasureSpec.getSize(h));}
 protected void setMeasuredDimension(int w,int h){measuredWidth=w;measuredHeight=h;}
 public final void layout(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;onLayout(true,l,t,r,b);}protected void onLayout(boolean changed,int l,int t,int r,int b){}
 protected void onDraw(Canvas c){}public void draw(Canvas c){onDraw(c);}
}''',
'android/view/ViewGroup.java': '''package android.view;import android.content.Context;import java.util.*;public class ViewGroup extends View {public final List<View> children=new ArrayList<>();public ViewGroup(Context c){super(c);}public void setWillNotDraw(boolean b){}public void addView(View v){children.add(v);}}''',
'android/widget/Button.java': '''package android.widget;import android.content.Context;import android.view.View;public class Button extends View {public Button(Context c){super(c);}public void setText(CharSequence s){}public void setMinWidth(int w){}public void setMinHeight(int h){} }''',
'com/msa/freedoom/R.java': '''package com.msa.freedoom;public final class R {public static final class drawable {public static final int fnaf_menu_art=1;}}''',
}
for path, content in files.items():
    f = r / path
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(content + '\n', encoding='utf-8')
print(f'Generated {len(files)} menu Android fakes in {r}')
