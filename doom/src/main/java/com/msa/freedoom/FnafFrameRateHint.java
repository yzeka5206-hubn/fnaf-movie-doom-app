package com.msa.freedoom;

import android.os.Build;
import android.view.Surface;
import android.view.SurfaceHolder;

/** Android 11+ display-scheduler hint, not an FPS limiter or a promise. */
public final class FnafFrameRateHint {
    private FnafFrameRateHint() {}

    public static void request(SurfaceHolder holder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || holder == null) return;
        try {
            Surface surface = holder.getSurface();
            if (surface != null && surface.isValid()) {
                // DEFAULT is for games. Do not force a non-seamless mode switch.
                // Reissued only when a new SDL surface is created.
                surface.setFrameRate(60.0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Driver/ROM may reject the hint or destroy the surface concurrently.
            // Keep SDL's old timing behavior instead of interrupting the game.
        }
    }
}
