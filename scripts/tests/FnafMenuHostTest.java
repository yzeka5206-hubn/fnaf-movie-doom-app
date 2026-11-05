package com.msa.freedoom;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FnafMenuHostTest {
    static void check(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }
    static void eq(float a, float b) { check(Math.abs(a-b)<0.02f, a+" != "+b); }
    static void box(FnafMenuGeometry.Box b, float l,float t,float r,float d) {
        eq(b.left,l);eq(b.top,t);eq(b.right,r);eq(b.bottom,d);
    }
    static boolean inside(FnafMenuGeometry.Box b,float x,float y) {
        return x>=b.left && x<=b.right && y>=b.top && y<=b.bottom;
    }
    static void layout(FnafMenuView menu, int w,int h) {
        menu.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),
                     View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
        menu.layout(0,0,w,h);
    }
    public static void main(String[] args) {
        FnafMenuGeometry.Fit f=FnafMenuGeometry.fit(1920,1080);
        eq(f.scale,1);eq(f.left,0);eq(f.top,0);
        f=FnafMenuGeometry.fit(2400,1080);
        eq(f.scale,1);eq(f.left,240);eq(f.top,0);
        box(f.place(FnafMenuGeometry.bounds(FnafMenuGeometry.Item.SINGLEPLAYER)),332,590,975,684);
        f=FnafMenuGeometry.fit(800,480);
        eq(f.scale,800f/1920f);eq(f.left,0);eq(f.top,15);
        // Known text/icon centers in the supplied, cropped 1920 x 1080 image.
        check(inside(FnafMenuGeometry.bounds(FnafMenuGeometry.Item.SINGLEPLAYER),360,640),"singleplayer text center");
        check(inside(FnafMenuGeometry.bounds(FnafMenuGeometry.Item.MULTIPLAYER),350,735),"multiplayer text center");
        check(inside(FnafMenuGeometry.bounds(FnafMenuGeometry.Item.OPTIONS),260,830),"options text center");
        check(inside(FnafMenuGeometry.bounds(FnafMenuGeometry.Item.YOUTUBE),1550,110),"YouTube icon center");
        check(inside(FnafMenuGeometry.bounds(FnafMenuGeometry.Item.DISCORD),1680,110),"Discord icon center");
        check(inside(FnafMenuGeometry.bounds(FnafMenuGeometry.Item.VOLUME),1810,110),"volume icon center");
        FnafMenuGeometry.Item[] items=FnafMenuGeometry.Item.values();
        for(int i=0;i<items.length;i++)for(int j=i+1;j<items.length;j++){
            FnafMenuGeometry.Box a=FnafMenuGeometry.bounds(items[i]), b=FnafMenuGeometry.bounds(items[j]);
            check(a.right<=b.left || b.right<=a.left || a.bottom<=b.top || b.bottom<=a.top,
                  "hitboxes overlap: "+items[i]+", "+items[j]);
        }
        eq(FnafMenuGeometry.fit(0,0).scale,0);
        System.out.println("PASS 16:9, 20:9, letterboxed, zero-size layout and all non-overlapping artwork hitboxes");

        List<String> calls=new ArrayList<>();
        FnafMenuView menu=new FnafMenuView(new Context(),new FnafMenuView.Listener(){
            public void onSingleplayer(){calls.add("single");}
            public void onMultiplayer(){calls.add("multi-disabled-explanation");}
            public void onOptions(){calls.add("options");}
            public void onYouTube(){calls.add("youtube");}
            public void onDiscord(){calls.add("discord");}
            public void onVolume(){calls.add("volume");}
        });
        layout(menu,2400,1080);
        check(menu.children.size()==6,"six real Android buttons");
        View first=menu.children.get(0);
        check(first.left==332 && first.top==590 && first.right==975 && first.bottom==684,"20:9 singleplayer layout");
        for(View v:menu.children){check(v.getContentDescription()!=null,"accessible button");check(v.performClick(),"real click");}
        check(calls.equals(Arrays.asList("single","multi-disabled-explanation","options","youtube","discord","volume")),"dispatch to correct actions: "+calls);
        menu.setBusy(true);
        for(View v:menu.children)check(!v.performClick(),"busy prevents double-tap and navigation");
        Canvas canvas=new Canvas();menu.draw(canvas);
        check(canvas.bitmapCount==1 && canvas.text.contains("KAPALI") && canvas.text.contains("OYUN HAZIRLANIYOR..."),"busy and disabled indicators drawn");
        menu.setBusy(false);check(first.performClick(),"click restored after preparing");
        layout(menu,800,480);check(first.left==38 && first.top==261 && first.right==306 && first.bottom==300,"4:3 fit layout and rounding");
        System.out.println("PASS real menu view button dispatch, accessibility, busy lock, status drawing and resized layout");
        System.out.println("ALL NATIVE FNAF MENU VIEW HOST TESTS PASSED");
    }
}
