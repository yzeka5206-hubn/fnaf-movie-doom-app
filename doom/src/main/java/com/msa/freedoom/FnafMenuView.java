package com.msa.freedoom;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.EnumMap;

/** Native Android view. The reference image is static; real accessible controls sit above it. */
public final class FnafMenuView extends ViewGroup {
    public interface Listener {
        void onSingleplayer();
        void onMultiplayer();
        void onOptions();
        void onYouTube();
        void onDiscord();
        void onVolume();
    }

    private final Bitmap art;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final EnumMap<FnafMenuGeometry.Item, Button> buttons =
            new EnumMap<>(FnafMenuGeometry.Item.class);
    private boolean busy;

    public FnafMenuView(Context context, Listener listener) {
        super(context);
        setWillNotDraw(false);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        art = BitmapFactory.decodeResource(getResources(), R.drawable.fnaf_menu_art, options);
        if (art == null) throw new IllegalStateException("Menu artwork is missing");
        addButton(FnafMenuGeometry.Item.SINGLEPLAYER, "Tek oyunculu oyunu başlat", listener::onSingleplayer);
        addButton(FnafMenuGeometry.Item.MULTIPLAYER, "Çok oyunculu kullanılamıyor; oyun tek oyunculu", listener::onMultiplayer);
        addButton(FnafMenuGeometry.Item.OPTIONS, "Ayarları ve yedeklemeyi aç", listener::onOptions);
        addButton(FnafMenuGeometry.Item.YOUTUBE, "Oyunun YouTube videosunu aç", listener::onYouTube);
        addButton(FnafMenuGeometry.Item.DISCORD, "Oyunun topluluk Discord bağlantısını aç", listener::onDiscord);
        addButton(FnafMenuGeometry.Item.VOLUME, "Telefonun medya ses düzeyini ayarla", listener::onVolume);
    }

    private void addButton(FnafMenuGeometry.Item item, String label, Runnable action) {
        Button button = new Button(getContext());
        button.setText("");
        button.setContentDescription(label);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x55ffffff),
                new ColorDrawable(Color.TRANSPARENT),
                new ColorDrawable(Color.WHITE)));
        button.setOnClickListener(v -> action.run());
        buttons.put(item, button);
        addView(button);
    }

    public void setBusy(boolean value) {
        busy = value;
        for (Button button : buttons.values()) button.setEnabled(!value);
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        FnafMenuGeometry.Fit fit = FnafMenuGeometry.fit(width, height);
        for (FnafMenuGeometry.Item item : FnafMenuGeometry.Item.values()) {
            FnafMenuGeometry.Box box = fit.place(FnafMenuGeometry.bounds(item));
            int w = Math.max(0, Math.round(box.right) - Math.round(box.left));
            int h = Math.max(0, Math.round(box.bottom) - Math.round(box.top));
            buttons.get(item).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        FnafMenuGeometry.Fit fit = FnafMenuGeometry.fit(right - left, bottom - top);
        for (FnafMenuGeometry.Item item : FnafMenuGeometry.Item.values()) {
            FnafMenuGeometry.Box box = fit.place(FnafMenuGeometry.bounds(item));
            buttons.get(item).layout(Math.round(box.left), Math.round(box.top),
                    Math.round(box.right), Math.round(box.bottom));
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        FnafMenuGeometry.Fit fit = FnafMenuGeometry.fit(getWidth(), getHeight());
        if (fit.scale <= 0) return;
        canvas.save();
        canvas.translate(fit.left, fit.top);
        canvas.scale(fit.scale, fit.scale);
        canvas.drawBitmap(art, 0, 0, bitmapPaint);

        // Keep the original label visible but clearly mark it as unavailable.
        overlayPaint.setColor(0x99000000);
        canvas.drawRect(92, 686, 735, 779, overlayPaint);
        overlayPaint.setColor(0xffdddddd);
        overlayPaint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        overlayPaint.setTextSize(27);
        canvas.drawText("KAPALI", 625, 738, overlayPaint);

        // The original author credits in the reference remain unobscured.
        overlayPaint.setColor(0xffbdbdbd);
        overlayPaint.setTextSize(18);
        canvas.drawText("ANDROID UYARLAMASI", 790, 1057, overlayPaint);
        if (busy) {
            overlayPaint.setColor(0xdd000000);
            canvas.drawRect(92, 911, 735, 987, overlayPaint);
            overlayPaint.setColor(Color.WHITE);
            overlayPaint.setTextSize(34);
            canvas.drawText("OYUN HAZIRLANIYOR...", 118, 961, overlayPaint);
        }
        canvas.restore();
    }
}
