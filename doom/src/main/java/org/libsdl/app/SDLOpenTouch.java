package org.libsdl.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Message;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.beloko.touchcontrols.ControlInterpreter;
import com.beloko.touchcontrols.TouchSettings;

import com.msa.freedoom.AppSettings;
import com.msa.freedoom.Utils;

/**
 * Freedoom's minimal replacement for emileb's AndroidCore SDLOpenTouch.
 *
 * org.libsdl.app2012.SDLActivity (the vendored SDL2 Java glue) calls these
 * static hooks; the original routed them into the full AndroidCore framework.
 * This version wires them to the app's own AppSettings plus the vendored
 * com.beloko.touchcontrols ControlInterpreter, and boots the engine through
 * NativeLib.init() (which never returns; the engine owns the thread).
 */
public class SDLOpenTouch
{
    static final String TAG = "SDLOpenTouch";

    static float resDiv = 1.0f;
    static boolean divDone = false;

    // The engine renders at a fixed landscape resolution for the whole
    // session (it cannot resize after init); the Game activity is locked to
    // sensorLandscape so the surface never changes aspect mid-game.
    static int fbWidth = 0;
    static int fbHeight = 0;

    public static boolean swapMouseXY = false;
    public static boolean invertMouseX = false;
    public static boolean invertMouseY = false;

    static NativeLib engine;
    static long lastBackPressMs = 0;

    public static ControlInterpreter controlInterp;

    // Bridge references set by SDLActivity so this class stays independent of
    // the package-specific SDL classes.
    static View surfaceView;
    static Runnable audioPauseCallback;
    static Runnable audioResumeCallback;
    static Runnable enableRelativeMouseCallback;

    public static void setBridge(View surface, Runnable audioPause, Runnable audioResume, Runnable enableRelativeMouse)
    {
        surfaceView = surface;
        audioPauseCallback = audioPause;
        audioResumeCallback = audioResume;
        enableRelativeMouseCallback = enableRelativeMouse;
    }

    public static void onPause(Context context)
    {
        if (audioPauseCallback != null)
            audioPauseCallback.run();
    }

    public static void onResume(Context context)
    {
        if (audioResumeCallback != null)
            audioResumeCallback.run();
    }

    public static void Setup(Activity activity, Intent intent)
    {
        AppSettings.INSTANCE.reloadSettings(activity.getApplication());

        NativeConsoleBox.init(activity);

        // fullscreen + keep screen on
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Utils.setImmersionMode(activity);

        engine = new NativeLib();

        controlInterp = new ControlInterpreter(
                engine,
                Utils.getGameGamepadConfig(activity.getResources()),
                TouchSettings.gamePadControlsFile,
                TouchSettings.gamePadEnabled);

        int div = intent.getIntExtra("res_div", 1);
        resDiv = 1.0f / div;
    }

    public static void RunApplication(Activity activity, Intent intent, float displayWidth, float displayHeight)
    {
        String args = intent.getStringExtra("args");
        if (args == null)
            args = "";

        // The engine resolution is fixed for the whole session: always the
        // landscape dimensions of the surface, regardless of the orientation
        // the game was launched in.
        fbWidth = (int) Math.max(displayWidth, displayHeight);
        fbHeight = (int) Math.min(displayWidth, displayHeight);

        // Force emileb's GLES backend: the default (1 = Vulkan) cannot host
        // the GL-drawn touch controls, which render inside the engine's
        // swap-buffer callback. The GLES framebuffer takes its size from
        // vid_defwidth/vid_defheight (desktop default 640x480), so pass the
        // fixed landscape size, like Delta Touch's $W/$H substitution does.
        // Ic render cozunurlugu ekranin 2/3'u (11T'de 1600x720): FPS dostu,
        // dokunma koordinatlari tam ekran uzerinden calismaya devam eder.
        int renderW = fbWidth * 2 / 3;
        int renderH = fbHeight * 2 / 3;
        args += " +set vid_preferbackend 2 +set vid_fullscreen 1"
                + " +set vid_defwidth " + renderW
                + " +set vid_defheight " + renderH
                // FNAF Movie Doom mobil dusuk-grafik profili (FPS oncelikli)
                + " +set CL_GraphicPreset 0 +set CL_ResIndex 0"
                + " +set CL_TextureQuality 0 +set CL_ShadowQuality 0"
                + " +set CL_SSAO 0 +set CL_Bloom 0 +set CL_Lens 0 +set CL_Antialiasing 0"
                + " +set gl_ssao 0 +set gl_fxaa 0 +set gl_light_shadowmap 0"
                + " +set vid_maxfps 60"
                // KRITIK: PC'de Launcher motoru dogrudan OUTSIDE haritasina sokar.
                // Bu komut olmazsa motor TITLEMAP baslik ekraninda kalir; orada
                // her tus menuyu acar ve gercek bolum/yuruyus hic baslamaz.
                + " +set sv_difficulty 1 +map OUTSIDE";

        String[] argsArray = Utils.createArgs(args);

        String gamePath = intent.getStringExtra("game_path");

        int options = 0;
        // The engine renders with its own GLES3 backend; the touch controls
        // draw with the matching GLES context.
        options |= 0x10; // GAME_OPTION_GLES3
        // FNAF: hazir menu/oyun dokunmatik duzenini gizle; kendi katmanimiz (Game.kt)
        // kullanilacak. Hazir duzen ara sahnelerde ekrana USE+ENTER gonderip
        // ana menuyu aciyordu.
        options |= 0x2; // GAME_OPTION_HIDE_MENU_AND_GAME

        int gameType = 1; // GAME_TYPE_DOOM
        int wheelNbr = 10;

        String userFiles = gamePath + "/user_files";
        new java.io.File(userFiles).mkdirs();
        String logFilename = userFiles + "/gzdoom_log.txt";
        String tmpFiles = activity.getCacheDir().getAbsolutePath();
        String sourceDir = activity.getApplicationContext().getApplicationInfo().sourceDir;
        String nativeSoPath = activity.getApplicationInfo().nativeLibraryDir;
        String pngFiles = activity.getFilesDir().getAbsolutePath();

        Utils.copyPNGAssets(activity, pngFiles);

        Log.v(TAG, "native .so path = " + nativeSoPath);
        Log.v(TAG, "gamePath = " + gamePath);
        Log.v(TAG, "userFiles = " + userFiles);

        NativeLib.audioOverride(0, 0);

        // Only secondary_path may be null in the glue; every other string is
        // dereferenced unconditionally.
        String resDir = gamePath + "/res";

        // Never returns: the engine main loop runs on this (the SDL) thread.
        NativeLib.init(pngFiles + "/", options, wheelNbr, argsArray, gameType, gamePath,
                null, logFilename, nativeSoPath, userFiles, tmpFiles, sourceDir, resDir);
    }

    public static boolean surfaceChanged(Context context, SurfaceHolder holder, int width, int height)
    {
        Log.v(TAG, "surfaceChanged: " + width + " x " + height);

        if (resDiv != 1.0f && !divDone)
        {
            holder.setFixedSize((int) ((width * resDiv) + 0.5f), (int) ((height * resDiv) + 0.5f));
            divDone = true;
            return true;
        }

        NativeLib.setScreenSize(width, height);

        if (controlInterp != null)
            controlInterp.setScreenSize(width, height);

        if (enableRelativeMouseCallback != null)
            enableRelativeMouseCallback.run();

        return false;
    }

    public static boolean onTouchEvent(MotionEvent event)
    {
        // FNAF: SDL yuzeyine gelen dokunmalari yut. Hazir native duzenin
        // "blank" grubundaki gorunmez dev buton her dokunusu USE+ENTER'a
        // cevirip menuyu aciyordu. Tum giris Game.kt'deki kendi katmanimizdan.
        return true;
    }

    // --- FNAF kendi dokunmatik katmani koprusu (Game.kt kullanir) ---
    // action kodlari: SPEED=10 USE=11 JUMP=12 ATTACK=13 CROUCH=15 ALT_ATTACK=64
    public static void fnafAction(int state, int action)
    {
        if (engine != null)
            engine.doAction_if(state, action);
    }

    public static void fnafMove(float fwd, float side)
    {
        if (engine != null)
        {
            engine.analogFwd_if(fwd);
            engine.analogSide_if(side);
        }
    }

    public static void fnafLook(float pitch, float yaw)
    {
        if (engine != null)
        {
            engine.analogPitch_if(0, pitch); // LOOK_MODE_MOUSE (birikimli)
            engine.analogYaw_if(0, yaw);
        }
    }

    public static void fnafMenu()
    {
        NativeLib.backButton();
    }

    // Konsol komutu gonder (ornegin "+altattack" / "-altattack"): motorun
    // kendi komut kuyrugu uzerinden, PC'deki tus baglama sistemiyle ayni yol.
    public static void fnafCommand(String cmd)
    {
        NativeLib.executeCommand(cmd);
    }

    public static boolean onKey(int keyCode, KeyEvent event)
    {
        int source = event.getSource();
        // Stop right mouse button being backbutton
        if ((source == InputDevice.SOURCE_MOUSE) || (source == InputDevice.SOURCE_MOUSE_RELATIVE))
        {
            return true;
        }

        // Geri tusu = ESC, ama kenar kaydirma hareketleri de Android'de
        // KEYCODE_BACK uretir ve analog cubuklari kullanirken yanlislikla
        // duraklatma menusunu acar. Bu yuzden: ilk basış yutulur, 1.5 sn
        // icinde gelen ikinci basış menuyu acar. Ekrandaki sol-ust geri
        // butonu (native katman) tek dokunusla calismaya devam eder.
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (event.getAction() == KeyEvent.ACTION_DOWN)
            {
                long now = android.os.SystemClock.uptimeMillis();
                if (now - lastBackPressMs < 1500)
                {
                    lastBackPressMs = 0;
                    NativeLib.backButton();
                }
                else
                {
                    lastBackPressMs = now;
                }
            }
            return true;
        }

        if (controlInterp == null)
            return false;

        if (event.getAction() == KeyEvent.ACTION_DOWN)
        {
            return controlInterp.onKeyDown(keyCode, event);
        }
        else if (event.getAction() == KeyEvent.ACTION_UP)
        {
            return controlInterp.onKeyUp(keyCode, event);
        }

        return false;
    }

    // Sent by the native exit() override (Clibs_OpenTouch android_jni_inc.cpp)
    // when the engine terminates; the process must die so the next launch
    // starts with clean native state.
    static final int COMMAND_EXIT_APP = 0x8007;

    // Sent by the native vibrate channel (TouchInterfaceBase::vibrate and the
    // fire/hit haptic hooks). msg.obj is the Integer duration in milliseconds.
    static final int COMMAND_VIBRATE = 0x8005;

    public static boolean CommandHandler(Activity activity, Message msg)
    {
        if (msg.arg1 == COMMAND_EXIT_APP)
        {
            Log.v(TAG, "COMMAND_EXIT_APP: finishing");
            activity.finish();
            android.os.Process.killProcess(android.os.Process.myPid());
            return true;
        }
        if (msg.arg1 == COMMAND_VIBRATE)
        {
            // Read the pref straight from disk: the :Game process does not run
            // AppSettings.reloadSettings, so the static field is unreliable here.
            if (AppSettings.getBoolOption(activity, "vibrate", true))
            {
                int ms = (msg.obj instanceof Integer) ? (Integer) msg.obj : 30;
                try
                {
                    Vibrator v;
                    if (Build.VERSION.SDK_INT >= 31)
                    {
                        VibratorManager vm = (VibratorManager)
                            activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                        v = vm.getDefaultVibrator();
                    }
                    else
                    {
                        v = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
                    }
                    if (v != null && v.hasVibrator())
                    {
                        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                }
                catch (Exception e)
                {
                    Log.w(TAG, "vibrate failed", e);
                }
            }
            return true;
        }
        return false;
    }
}
