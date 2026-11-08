package com.fnaf.moviedoom.repair;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Offline-only one-time updater; leaves the original game and installed apps untouched. */
public class MainActivity extends Activity {
    private static final String OLD_PACKAGE = "com.fnaf.moviedoom.optimized";
    private static final int PICK_APK = 7;
    private TextView status;
    private Button prepare, select;
    private ProgressBar progress;
    private volatile boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(20, 23, 29));
        getWindow().setNavigationBarColor(Color.rgb(20, 23, 29));
        int pad = dp(22);
        ScrollView scroller = new ScrollView(this); scroller.setFillViewport(true);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, dp(32), pad, dp(32)); body.setBackgroundColor(Color.rgb(20, 23, 29));
        scroller.addView(body);
        TextView title = text("FNAF FPS GÜNCELLEMESİ", 25, true);
        body.addView(title);
        TextView desc = text("Yeni FPS denemesini yüklü FNAF Optimize veya Dış Siyah sürümünden hazırlar. " +
                "Büyük oyun dosyasını yeniden indirmez; internet kullanmaz. Telefonda en az 2 GB boş alan önerilir.\n\n" +
                "Yüklü sürümü otomatik tanır. Yeni APK aynı oyunun güncellemesidir; oyunu silme, üzerine yükle. 60 FPS hedeftir, garanti değildir.", 16, false);
        desc.setPadding(0, dp(16), 0, dp(18)); body.addView(desc);
        prepare = new Button(this); prepare.setText("YENİ APK'YI HAZIRLA");
        body.addView(prepare, new LinearLayout.LayoutParams(-1, -2));
        select = new Button(this); select.setText("ESKİ OYUN APK DOSYASINI SEÇ");
        body.addView(select, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = text("Üstteki düğme öncelikle kurulu testi kullanır. Sadece otomatik okuma başarısızsa alttaki düğmeyle önceki Optimize veya Dış Siyah oyun APK dosyasını seç.", 14, false);
        hint.setPadding(0, dp(12), 0, dp(8)); body.addView(hint);
        progress = new ProgressBar(this); progress.setIndeterminate(true); progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(36), dp(36)); pp.gravity = Gravity.CENTER_HORIZONTAL; body.addView(progress, pp);
        status = text("Hazır.", 17, true); status.setPadding(0, dp(20), 0, 0); body.addView(status);
        prepare.setOnClickListener(v -> useInstalledApk());
        select.setOnClickListener(v -> pickApk());
        setContentView(scroller);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextColor(Color.WHITE); t.setTextSize(sp);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setLineSpacing(dp(2), 1f); return t;
    }
    private int dp(float x) { return (int)(getResources().getDisplayMetrics().density * x + 0.5f); }

    private void useInstalledApk() {
        if (busy) return;
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(OLD_PACKAGE, 0);
            File apk = new File(info.publicSourceDir);
            if (!apk.isFile() || !apk.canRead()) {
                status.setText("Kurulu test APK'sı okunamıyor. Alttaki düğmeden önceki Optimize veya Dış Siyah oyun APK dosyasını seç."); return;
            }
            runPatch(() -> new FileInputStream(apk));
        } catch (PackageManager.NameNotFoundException | SecurityException e) {
            status.setText("FNAF Optimize Testi bulunamadı veya okunamıyor. Alttaki düğmeden önceki Optimize veya Dış Siyah oyun APK dosyasını seç.");
        }
    }
    private void pickApk() {
        if (busy) return;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, PICK_APK);
        } catch (Exception e) { status.setText("Dosya seçici açılamadı: " + safeMessage(e)); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_APK && result == RESULT_OK && data != null && data.getData() != null) {
            Uri source = data.getData();
            runPatch(() -> {
                InputStream in = getContentResolver().openInputStream(source);
                if (in == null) throw new IOException("Seçilen dosya açılamadı.");
                return in;
            });
        }
    }
    interface InputOpener { InputStream open() throws IOException; }
    private void runPatch(InputOpener source) {
        busy = true; prepare.setEnabled(false); select.setEnabled(false); progress.setVisibility(View.VISIBLE);
        status.setText("Hazırlanıyor ve güvenliği doğrulanıyor… Lütfen uygulamayı açık tut.");
        new Thread(() -> {
            Uri outputUri = null;
            ContentResolver cr = getContentResolver();
            try {
                // Select the supported installed optimized test by its complete SHA.
                // A different APK is rejected before any output is created.
                PatchEngine.SourceSignature signature;
                try (InputStream original = source.open()) { signature = PatchEngine.identify(original); }
                String selectedPatch = null;
                for (String candidate : new String[]{"repair-v13.dat", "repair-v14.dat"}) {
                    try (InputStream header = getAssets().open(candidate)) {
                        if (PatchEngine.matchesSource(header, signature)) { selectedPatch = candidate; break; }
                    }
                }
                if (selectedPatch == null) throw new IOException("Bu APK desteklenen Optimize veya Dış Siyah oyun sürümü değil.");
                ContentValues meta = new ContentValues();
                meta.put(MediaStore.Downloads.DISPLAY_NAME, "FNAF_FPS60_Test.apk");
                meta.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive");
                meta.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                meta.put(MediaStore.Downloads.IS_PENDING, 1);
                outputUri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, meta);
                if (outputUri == null) throw new IOException("İndirilenler klasörüne dosya oluşturulamadı.");
                try (InputStream original = source.open(); InputStream patch = getAssets().open(selectedPatch);
                     OutputStream target = cr.openOutputStream(outputUri, "w")) {
                    if (target == null) throw new IOException("Yeni APK yazılamadı.");
                    PatchEngine.apply(original, patch, target);
                }
                ContentValues complete = new ContentValues(); complete.put(MediaStore.Downloads.IS_PENDING, 0);
                if (cr.update(outputUri, complete, null, null) != 1) throw new IOException("Yeni APK tamamlanamadı.");
                String fileName = displayName(cr, outputUri);
                runOnUiThread(() -> finishWork("Hazır ve doğrulandı!\n\n1. FNAF oyununu SİLME. Yeni APK aynı uygulamanın üzerine güncelleme olarak kurulacak.\n\n2. Dosyalar → İndirilenler → " + fileName + " dosyasını aç ve yükle.\n\nKayıtlar korunarak güncellenmesi amaçlanır. İstersen işlemden sonra bu güncelleyiciyi kaldırabilirsin."));
            } catch (Exception e) {
                if (outputUri != null) try { cr.delete(outputUri, null, null); } catch (Exception ignored) { }
                String message = safeMessage(e);
                runOnUiThread(() -> finishWork("Hazırlanamadı: " + message + "\n\nOptimize veya Dış Siyah sürümü kuruluysa tekrar dene. Gerekirse önceki Optimize veya Dış Siyah oyun APK dosyasını seç. Hiçbir uygulama silinmedi."));
            }
        }, "offline-apk-repair").start();
    }
    private void finishWork(String message) {
        busy = false; progress.setVisibility(View.GONE); prepare.setEnabled(true); select.setEnabled(true); status.setText(message);
    }
    private static String displayName(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) { String n = c.getString(0); if (n != null && !n.isEmpty()) return n; }
        } catch (Exception ignored) { }
        return "FNAF_FPS60_Test.apk";
    }
    private static String safeMessage(Throwable t) {
        String s = t.getMessage();
        return s == null || s.isEmpty() ? t.getClass().getSimpleName() : s;
    }
}
