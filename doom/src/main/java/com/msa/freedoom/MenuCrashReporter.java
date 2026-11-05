package com.msa.freedoom;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local-only diagnostics for the experimental menu process.
 * No logs are uploaded. Native process exits cannot be caught by Java; use the
 * Android process-exit record when available. The gameplay process is untouched.
 */
final class MenuCrashReporter {
    private static final String REPORT_NAME = "original-menu-error.txt";
    private static final int MAX_CHARS = 12_000;
    private static final AtomicBoolean installed = new AtomicBoolean();
    private MenuCrashReporter() { }

    private static File reportFile(Context context) {
        return new File(context.getFilesDir(), REPORT_NAME);
    }

    /** Call in the parent immediately before a new menu launch. */
    static void clear(Context context) {
        try { Files.deleteIfExists(reportFile(context).toPath()); } catch (Exception ignored) { }
    }

    /** Call in the dedicated menu process before SDL Activity initialization. */
    static void install(Context context) {
        if (!installed.compareAndSet(false, true)) return;
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { writeJavaFailure(app, thread, error); } catch (Throwable ignored) { }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
    }

    private static void writeJavaFailure(Context context, Thread thread, Throwable error) throws Exception {
        StringWriter text = new StringWriter();
        PrintWriter writer = new PrintWriter(text);
        writer.println("Android menü hatası (" + thread.getName() + ")");
        Throwable root = error;
        for (int depth = 0; depth < 32 && root.getCause() != null && root.getCause() != root; depth++)
            root = root.getCause();
        if (root != error) writer.println("Asıl neden: " + root);
        error.printStackTrace(writer);
        writer.flush();
        String report = text.toString();
        if (report.length() > MAX_CHARS) report = report.substring(0, MAX_CHARS) + "\n...";
        // Same private directory is shared by this app's own isolated processes.
        try (FileOutputStream output = new FileOutputStream(reportFile(context))) {
            output.write(report.getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
    }

    /** Returns only this app's recent menu crash; intentional Back is not an error. */
    static String diagnose(Context context, long launchedAtMillis) {
        try {
            File file = reportFile(context);
            if (file.isFile() && file.lastModified() >= launchedAtMillis - 1000L) {
                String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                if (!text.trim().isEmpty()) return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
            }
        } catch (Exception ignored) { }
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            if (manager == null) return null;
            List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 20);
            String processName = context.getPackageName() + ":OriginalMenu";
            for (ApplicationExitInfo exit : exits) {
                if (!processName.equals(exit.getProcessName()) || exit.getTimestamp() < launchedAtMillis - 1000L)
                    continue;
                int reason = exit.getReason();
                if (reason != ApplicationExitInfo.REASON_CRASH &&
                        reason != ApplicationExitInfo.REASON_CRASH_NATIVE &&
                        reason != ApplicationExitInfo.REASON_ANR &&
                        reason != ApplicationExitInfo.REASON_INITIALIZATION_FAILURE &&
                        reason != ApplicationExitInfo.REASON_LOW_MEMORY) continue;
                String detail = exit.getDescription();
                return "Menü işlemi kapandı. Android hata nedeni: " + reason +
                        ", durum: " + exit.getStatus() +
                        (detail == null ? "" : "\n" + detail);
            }
        } catch (Exception ignored) { }
        return null;
    }
}
