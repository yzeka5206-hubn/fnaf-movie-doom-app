package com.msa.freedoom

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.libsdl.app2012.SDLActivity
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Real converted launcher; SDL/OpenAL/GLES run natively, in a separate process. */
class OriginalMenuActivity : SDLActivity() {
    private val resultSent = AtomicBoolean(false)
    private var resolutionHeight = 480
    private var outsideBlack = true

    override fun useOpenTouch(): Boolean = false
    override fun getLibraries(): Array<String> = arrayOf("hidapi", "saffal", "openal", "SDL2", "Chowdren", "FnafMenuRunner")

    // The converted launcher creates a resizable SDL window without an
    // orientation hint. SDL would otherwise replace the manifest's landscape
    // setting with FULL_SENSOR and crop the menu when the phone is upright.
    // Scope this override to the menu; the v7 gameplay activity is unchanged.
    override fun setOrientationBis(w: Int, h: Int, resizable: Boolean, hint: String?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private external fun nativeRunConvertedLauncher(arguments: Array<String>): Int

    override fun runLauncherAfterSdlSetup(arguments: Array<String>) {
        val result = nativeRunConvertedLauncher(arguments)
        if (result != 0) android.util.Log.e("FnafMenuRunner", "Launcher returned status $result")
    }

    // The APK stores native libraries uncompressed; SONAME works even when
    // Android loads them directly from the APK instead of extracting files.
    override fun getMainSharedObject(): String = "libChowdren.so"
    override fun getArguments(): Array<String> = arrayOf(File(filesDir, "original-menu").absolutePath)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Capture Java startup failures in this process before SDL initialization.
        // Reports stay on this phone unless the user chooses to share them.
        MenuCrashReporter.install(this)
        super.onCreate(savedInstanceState)
        AppSettings.reloadSettings(application)
        Utils.setImmersionMode(this)
        volumeControlStream = AudioManager.STREAM_MUSIC
        val preferences = getSharedPreferences("fnaf_original_menu", Context.MODE_PRIVATE)
        resolutionHeight = preferences.getInt("resolution_height", 480).let { if (it == 720) 720 else 480 }
        outsideBlack = preferences.getBoolean("outside_black", true)
        addResolutionButton()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Utils.onWindowFocusChanged(this, hasFocus)
    }

    private fun addResolutionButton() {
        val dp = resources.displayMetrics.density
        fun styledButton(label: String) = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 13f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 12 * dp
                setColor(Color.argb(155, 0, 0, 0))
                setStroke((1 * dp).toInt(), Color.argb(140, 255, 255, 255))
            }
        }
        val resolution = styledButton("${resolutionHeight}p").apply {
            contentDescription = "Oyun çözünürlüğünü 480p ve 720p arasında değiştir"
            setOnClickListener {
                resolutionHeight = if (resolutionHeight == 480) 720 else 480
                text = "${resolutionHeight}p"
                getSharedPreferences("fnaf_original_menu", Context.MODE_PRIVATE)
                    .edit().putInt("resolution_height", resolutionHeight).apply()
                Toast.makeText(this@OriginalMenuActivity,
                    "Oyun çözünürlüğü: ${resolutionHeight}p", Toast.LENGTH_SHORT).show()
            }
        }
        val exterior = styledButton(if (outsideBlack) "DIŞ: SİYAH" else "DIŞ: NORMAL").apply {
            textSize = 11f
            contentDescription = "Gece birden beşe kadar dış görüntüyü siyah veya normal yap"
            setOnClickListener {
                outsideBlack = !outsideBlack
                text = if (outsideBlack) "DIŞ: SİYAH" else "DIŞ: NORMAL"
                getSharedPreferences("fnaf_original_menu", Context.MODE_PRIVATE)
                    .edit().putBoolean("outside_black", outsideBlack).commit()
                Toast.makeText(this@OriginalMenuActivity,
                    if (outsideBlack) "Geceler 1–5: dışarısı siyah" else "Geceler 1–5: dışarısı normal",
                    Toast.LENGTH_SHORT).show()
            }
        }
        val backup = styledButton("YEDEK").apply {
            contentDescription = "Kayıtları yedekle veya geri yükle"
            setOnClickListener { startActivity(Intent(this@OriginalMenuActivity, FnafOptionsActivity::class.java)) }
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val one = LinearLayout.LayoutParams((68 * dp).toInt(), (36 * dp).toInt())
            addView(resolution, one)
            val two = LinearLayout.LayoutParams((68 * dp).toInt(), (36 * dp).toInt()).apply {
                marginStart = (8 * dp).toInt()
            }
            addView(backup, two)
            val three = LinearLayout.LayoutParams((104 * dp).toInt(), (36 * dp).toInt()).apply {
                marginStart = (8 * dp).toInt()
            }
            addView(exterior, three)
        }
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = (12 * dp).toInt()
        }
        addContentView(controls, params)
    }

    // Called by native SDL thread; no shell or PC executable is ever launched.
    @Suppress("unused")
    fun onSingleplayerRequested(map: String, difficulty: Int, streamer: Int, graphics: Int,
                                shaders: Int, halloween: Int, christmas: Int) {
        runOnUiThread {
            if (isFinishing || resultSent.get()) return@runOnUiThread
            val selected = FnafMenuSelection(map, difficulty, streamer, graphics, shaders,
                halloween, christmas, resolutionHeight, outsideBlack)
            if (!selected.isValid()) {
                Toast.makeText(this, "Geçersiz oyun seçimi.", Toast.LENGTH_LONG).show()
                return@runOnUiThread
            }
            if (!resultSent.compareAndSet(false, true)) return@runOnUiThread
            val data = Intent()
            selected.writeTo(data)
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    @Suppress("unused")
    fun onLauncherNotice(code: String) {
        val message = when (code) {
            "MULTIPLAYER_DISABLED" -> "Bu sürüm yalnızca tek oyunculu."
            "PBR_UNAVAILABLE" -> "PC'ye ait PBR/shader paketi telefonda bulunmuyor."
            "API_GLES_ONLY" -> "Telefonda OpenGL ES kullanılıyor."
            else -> return
        }
        runOnUiThread { if (!isFinishing) Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    @Suppress("unused")
    fun onLauncherUrl(url: String) {
        if (url !in setOf("https://www.youtube.com/@Angenylo", "https://discord.gg/8wbaSJS4Sn",
                "https://gamejolt.com/games/FNaFMovieDoom/896445")) return
        runOnUiThread {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Bağlantı açılamadı.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Native launcher globals are not reentrant. The Activity has a dedicated
        // process, so this cannot kill the controller or the gameplay process.
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
