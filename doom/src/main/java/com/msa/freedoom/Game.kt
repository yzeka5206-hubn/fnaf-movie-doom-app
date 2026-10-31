package com.msa.freedoom

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.RandomAccessFile
import java.util.ArrayDeque
import org.libsdl.app.SDLOpenTouch
import org.libsdl.app2012.SDLActivity
import kotlin.math.hypot
import kotlin.math.min

/**
 * The gameplay activity. Since the rebase onto emileb's GZDoom 4.15 mobile
 * port this is a thin subclass of the vendored SDL2 SDLActivity: SDL owns the
 * surface and EGL context, and the engine main loop runs on the SDL thread
 * (SDLOpenTouch.RunApplication -> NativeLib.init, which never returns).
 *
 * Launch contract from the Compose launcher (unchanged): Intent extras
 * "args" (engine command line), "game_path" (Freedoom base dir),
 * "res_div" (resolution divider), "game" (selected IWAD index).
 *
 * FNAF Movie Doom: kendi dokunmatik katmanimiz burada. Hazir native duzen
 * ara sahnelerde her dokunusu USE+ENTER'a cevirip ana menuyu actigi icin
 * SDLOpenTouch tarafinda kapatildi (GAME_OPTION_HIDE_MENU_AND_GAME +
 * onTouchEvent yutma). Bu katman: solda dinamik analog cubuk, sagda
 * surukleyerek bakis, sagda AL/FENER/KOS/EGIL/ZIPLA butonlari ve ustte
 * MENU + menu gezinti (ENTER/yukari/asagi) butonlari sunar.
 */
class Game : SDLActivity() {

    // This build identifier is also checked in the compiled APK by CI.
    private val buildMarker = "FNAF_DIAG_V6_1_2026"
    private val diagnosticEvents = ArrayDeque<String>()
    private var analogMovementMode = false
    private lateinit var joystick: JoystickView
    private lateinit var logPanel: LinearLayout
    private lateinit var logText: TextView
    private var probeSerial = 0

    @Synchronized
    private fun recordEvent(message: String) {
        while (diagnosticEvents.size >= 160) diagnosticEvents.removeFirst()
        diagnosticEvents.addLast("${SystemClock.elapsedRealtime()}ms  $message")
    }

    @Synchronized
    private fun eventSnapshot(): String = diagnosticEvents.joinToString("\n")

    override fun getLibraries(): Array<String> = arrayOf(
        "hidapi",
        "saffal",
        "openal",
        "zmusic_uz",
        "touchcontrols",
        "SDL2",
        "uzdoom",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordEvent("Oyun aktivitesi acildi; surum=${BuildConfig.VERSION_NAME}")
        buildTouchOverlay()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Utils.setImmersionMode(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Utils.onWindowFocusChanged(this, hasFocus)
        // Analog cubuk bolgelerini sistem hareketlerinden (geri/ana ekran
        // kenar kaydirmalari) koru: cubugu suruklerken yanlislikla
        // duraklatma menusu acilmasin.
        if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dm = resources.displayMetrics
            val w = dm.widthPixels
            val h = dm.heightPixels
            window.decorView.systemGestureExclusionRects = listOf(
                Rect(0, h / 2, w / 3, h),
                Rect(2 * w / 3, h / 2, w, h),
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // The engine cannot re-initialise in the same process (static state,
        // and NativeLib.init never returns); kill the process so the next
        // launch starts clean.
        System.exit(0)
    }

    // ------------------------------------------------------------------
    // FNAF dokunmatik katmani
    // ------------------------------------------------------------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun makeButton(
        label: String,
        sizeDp: Int,
        onDown: () -> Unit,
        onUp: () -> Unit,
    ): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.setTextColor(Color.WHITE)
        tv.textSize = 13f
        tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.gravity = Gravity.CENTER
        tv.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(70, 0, 0, 0))
            setStroke(dp(2), Color.argb(170, 255, 255, 255))
        }
        tv.layoutParams = FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    recordEvent("TUS $label: BASILDI")
                    v.alpha = 0.6f
                    onDown()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val durum = if (e.actionMasked == MotionEvent.ACTION_CANCEL) "IPTAL" else "BIRAKILDI"
                    recordEvent("TUS $label: $durum")
                    v.alpha = 1f
                    onUp()
                }
            }
            true
        }
        return tv
    }

    private fun place(
        parent: FrameLayout,
        view: View,
        gravity: Int,
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
    ) {
        val lp = view.layoutParams as FrameLayout.LayoutParams
        lp.gravity = gravity
        lp.setMargins(dp(left), dp(top), dp(right), dp(bottom))
        view.layoutParams = lp
        parent.addView(view)
    }

    private fun keyTap(keyCode: Int): Pair<() -> Unit, () -> Unit> =
        Pair({ SDLActivity.onNativeKeyDown(keyCode) }, { SDLActivity.onNativeKeyUp(keyCode) })

    private fun buildTouchOverlay() {
        val root = FrameLayout(this)

        // 1) Tam ekran bakis alani (butonlarin altinda): surukle = bakis.
        val look = object : View(this) {
            private var pid = -1
            private var lastX = 0f
            private var lastY = 0f
            private var lastLogMs = 0L

            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        pid = e.getPointerId(0)
                        lastX = e.x
                        lastY = e.y
                        recordEvent("BAKIS: dokunuldu x=${e.x.toInt()} y=${e.y.toInt()}")
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> Unit
                    MotionEvent.ACTION_MOVE -> {
                        val i = e.findPointerIndex(pid)
                        if (i >= 0) {
                            val dx = e.getX(i) - lastX
                            val dy = e.getY(i) - lastY
                            lastX = e.getX(i)
                            lastY = e.getY(i)
                            // Iki yol birden: motorun fare bakisi (SDL relative mouse)
                            // ve analog birikimli bakis. Hangisini oyun kullanirsa.
                            SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE, dx, dy, true)
                            SDLOpenTouch.fnafLook(dy * 0.0016f, dx * 0.00055f)
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastLogMs >= 300) {
                                recordEvent("BAKIS: dx=${dx.toInt()} dy=${dy.toInt()} (SDL + analog)")
                                lastLogMs = now
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        recordEvent("BAKIS: birakildi/iptal")
                        pid = -1
                    }
                }
                return true
            }
        }
        root.addView(
            look,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        // 2) Sol dinamik analog cubuk (WASD yonunde analog hareket).
        val stick = JoystickView(this)
        joystick = stick
        stick.layoutParams = FrameLayout.LayoutParams(dp(190), dp(190))
        place(root, stick, Gravity.BOTTOM or Gravity.START, left = 26, bottom = 30)

        // 3) Sag butonlar: asagidan yukari AL, FENER, ZIPLA, EGIL, KOS.
        val al = makeButton("AL", 84,
            {
                SDLOpenTouch.fnafAction(1, ACT_ATTACK)
                SDLOpenTouch.fnafAction(1, ACT_USE)
                SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_E)
                SDLOpenTouch.fnafCommand("+attack")
            },
            {
                SDLOpenTouch.fnafAction(0, ACT_ATTACK)
                SDLOpenTouch.fnafAction(0, ACT_USE)
                SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_E)
                SDLOpenTouch.fnafCommand("-attack")
            })
        place(root, al, Gravity.BOTTOM or Gravity.END, right = 30, bottom = 36)

        val fener = makeButton("FENER", 70,
            {
                SDLOpenTouch.fnafAction(1, ACT_ALT_ATTACK)
                SDLOpenTouch.fnafCommand("+altattack")
            },
            {
                SDLOpenTouch.fnafAction(0, ACT_ALT_ATTACK)
                SDLOpenTouch.fnafCommand("-altattack")
            })
        place(root, fener, Gravity.BOTTOM or Gravity.END, right = 132, bottom = 36)

        val zipla = makeButton("ZIPLA", 64,
            {
                SDLOpenTouch.fnafAction(1, ACT_JUMP)
                SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_SPACE)
            },
            {
                SDLOpenTouch.fnafAction(0, ACT_JUMP)
                SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_SPACE)
            })
        place(root, zipla, Gravity.BOTTOM or Gravity.END, right = 40, bottom = 140)

        val egil = makeButton("EGIL", 64,
            {
                SDLOpenTouch.fnafAction(1, ACT_CROUCH)
                SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_CTRL_LEFT)
            },
            {
                SDLOpenTouch.fnafAction(0, ACT_CROUCH)
                SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_CTRL_LEFT)
            })
        place(root, egil, Gravity.BOTTOM or Gravity.END, right = 122, bottom = 128)

        val kos = makeButton("KOS", 64,
            {
                SDLOpenTouch.fnafAction(1, ACT_SPEED)
                SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT)
            },
            {
                SDLOpenTouch.fnafAction(0, ACT_SPEED)
                SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT)
            })
        place(root, kos, Gravity.BOTTOM or Gravity.END, right = 44, bottom = 224)

        // 4) Ust sol: MENU (bilerek, tek yerden duraklatma).
        val menu = makeButton("MENU", 58, { SDLOpenTouch.fnafMenu() }, {})
        place(root, menu, Gravity.TOP or Gravity.START, left = 18, top = 14)

        // LOG yalnızca uygulama panelini açar; motora ESC/ENTER göndermez.
        val logBtn = makeButton("LOG", 58, { openLogPanel() }, {})
        place(root, logBtn, Gravity.TOP or Gravity.START, left = 88, top = 14)

        // 5) Ust sag: menu gezintisi (ENTER / yukari / asagi).
        val (enterDown, enterUp) = keyTap(KeyEvent.KEYCODE_ENTER)
        val enter = makeButton("ENTER", 62, enterDown, enterUp)
        place(root, enter, Gravity.TOP or Gravity.END, right = 18, top = 14)

        val (upDown, upUp) = keyTap(KeyEvent.KEYCODE_DPAD_UP)
        val up = makeButton("▲", 52, upDown, upUp)
        place(root, up, Gravity.TOP or Gravity.END, right = 94, top = 14)

        val (downDown, downUp) = keyTap(KeyEvent.KEYCODE_DPAD_DOWN)
        val down = makeButton("▼", 52, downDown, downUp)
        place(root, down, Gravity.TOP or Gravity.END, right = 158, top = 14)

        // Add the panel last so its opaque background consumes touches while open.
        logPanel = createLogPanel()
        root.addView(
            logPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        addContentView(
            root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    /** UI events stay local; the player decides if and when to copy the report. */
    private fun createLogPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.rgb(12, 17, 20))
            isClickable = true
            visibility = View.GONE
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun addToolbarButton(label: String, action: (Button) -> Unit) {
            val button = Button(this).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                minimumHeight = 0
                setPadding(dp(4), 0, dp(4), 0)
            }
            button.setOnClickListener { action(button) }
            toolbar.addView(button, LinearLayout.LayoutParams(0, dp(42), 1f))
        }
        addToolbarButton("YENILE") {
            recordEvent("LOG: yenile")
            requestEngineProbe("LOG_YENILE", includeMapAndBinds = true)
            refreshLogPanel()
        }
        addToolbarButton("KOPYALA") {
            recordEvent("LOG: kopyala")
            val report = diagnosticReport()
            logText.text = report
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("FNAF tani kaydi", report))
            Toast.makeText(this, "Kopyalandi. Sohbete yapistirabilirsin.", Toast.LENGTH_LONG).show()
        }
        addToolbarButton("MOD: WASD") { button ->
            joystick.stopAll("MOD_DEGISIMI")
            analogMovementMode = !analogMovementMode
            button.text = if (analogMovementMode) "MOD: ANALOG" else "MOD: WASD"
            recordEvent("HAREKET MODU: ${if (analogMovementMode) "ANALOG" else "WASD"}")
            refreshLogPanel()
        }
        addToolbarButton("KAPAT") {
            recordEvent("LOG: kapat")
            panel.visibility = View.GONE
        }
        panel.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48),
        ))

        logText = TextView(this).apply {
            setTextColor(Color.rgb(191, 243, 210))
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(6), dp(8), dp(6), dp(8))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(logText)
        }
        panel.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        ))
        return panel
    }

    private fun openLogPanel() {
        joystick.stopAll("LOG_ACILDI")
        recordEvent("LOG: acildi")
        logPanel.visibility = View.VISIBLE
        // Read-only engine commands are queued, not direct proof that a frame consumed them.
        requestEngineProbe("LOG_ACILDI", includeMapAndBinds = true)
        refreshLogPanel()
    }

    private fun refreshLogPanel() {
        logText.text = diagnosticReport()
        // Commands are processed on the engine thread; show their output after a short delay.
        logPanel.postDelayed({
            if (logPanel.visibility == View.VISIBLE) logText.text = diagnosticReport()
        }, 1200)
    }

    private fun requestEngineProbe(label: String, includeMapAndBinds: Boolean = false) {
        if (!SDLOpenTouch.fnafCanQueryEngine()) {
            recordEvent("MOTOR SORGU $label: henuz hazir degil, atlandi")
            return
        }
        val marker = "FNAF_DIAG_${++probeSerial}_$label"
        try {
            SDLOpenTouch.fnafCommand("echo $marker")
            SDLOpenTouch.fnafCommand("currentpos")
            if (includeMapAndBinds) {
                SDLOpenTouch.fnafCommand("mapinfo")
                SDLOpenTouch.fnafCommand("bind w")
                SDLOpenTouch.fnafCommand("bind a")
                SDLOpenTouch.fnafCommand("bind s")
                SDLOpenTouch.fnafCommand("bind d")
            }
            recordEvent("MOTOR SORGU kuyruga eklendi: $marker")
        } catch (e: Exception) {
            recordEvent("MOTOR SORGU HATASI: ${e.javaClass.simpleName}: ${e.message}")
        } catch (e: LinkageError) {
            recordEvent("MOTOR KOPRU HATASI: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun diagnosticReport(): String = buildString {
        appendLine("$buildMarker | v${BuildConfig.VERSION_NAME}")
        appendLine("Cihaz: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE}")
        appendLine("Hareket modu: ${if (analogMovementMode) "ANALOG" else "WASD"}")
        appendLine("Kopru: ${SDLOpenTouch.fnafDebugInfo()}")
        appendLine("Deneme: Oyuna don, cubugu birkac saniye it, LOG > KOPYALA yap.")
        appendLine("MOD dugmesi ile WASD ve ANALOG ayri ayri denenebilir.")
        appendLine("LOG, yalnizca sen KOPYALA'ya basarsan paylasilir.")
        appendLine("Motor sorgulari kuyruga yazilir; motor islememisse cevap gorunmeyebilir.")
        appendLine()
        appendLine("=== SON KONTROL OLAYLARI ===")
        appendLine(eventSnapshot().ifEmpty { "Henuz olay yok." })
        appendLine()
        appendLine("=== MOTOR GUNLUGU (SON 24 KB) ===")
        append(readGameLog())
    }

    /** Read only a bounded tail: the engine can keep appending while the game runs. */
    private fun readGameLog(): String {
        val base = intent.getStringExtra("game_path") ?: return "game_path bulunamadi."
        val file = File(File(base, "user_files"), "gzdoom_log.txt")
        if (!file.isFile) return "Motor gunlugu henuz olusmadi: ${file.absolutePath}"
        return try {
            RandomAccessFile(file, "r").use { input ->
                val size = input.length()
                val byteCount = min(size, 24_000L).toInt()
                input.seek(size - byteCount)
                val bytes = ByteArray(byteCount)
                input.readFully(bytes)
                val prefix = if (size > byteCount) "[daha eski kisim kesildi]\n" else ""
                "Dosya boyutu: $size bayt\n$prefix${String(bytes, Charsets.UTF_8)}"
            }
        } catch (e: Exception) {
            "Motor gunlugu okunamadi: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /** Dynamic stick; only one input path is enabled at a time for diagnosis. */
    private inner class JoystickView(ctx: Context) : View(ctx) {
        private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(35, 255, 255, 255)
            style = Paint.Style.FILL
        }
        private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 255, 255, 255)
            style = Paint.Style.FILL
        }
        private var active = false
        private var centerX = 0f
        private var centerY = 0f
        private var knobX = 0f
        private var knobY = 0f
        private val pressed = mutableSetOf<Int>()
        private var analogSent = false
        private var probeStarted = false
        private var lastAnalogLogMs = 0L

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = if (active) centerX else width / 2f
            val cy = if (active) centerY else height / 2f
            val r = dp(62).toFloat()
            canvas.drawCircle(cx, cy, r, fillPaint)
            canvas.drawCircle(cx, cy, r, basePaint)
            canvas.drawCircle(if (active) knobX else cx, if (active) knobY else cy,
                dp(27).toFloat(), knobPaint)
        }

        private fun releaseInputs() {
            for (key in pressed.toList()) {
                SDLActivity.onNativeKeyUp(key)
                recordEvent("CUBUK SDL TUS YUKARI: ${KeyEvent.keyCodeToString(key)}")
            }
            pressed.clear()
            if (analogSent) {
                SDLOpenTouch.fnafMove(0f, 0f)
                analogSent = false
                recordEvent("CUBUK ANALOG: sifirlandi")
            }
        }

        fun stopAll(reason: String) {
            releaseInputs()
            if (active && probeStarted) requestEngineProbe("CUBUK_BITIS_$reason")
            if (active) recordEvent("CUBUK: durduruldu ($reason)")
            active = false
            probeStarted = false
            invalidate()
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val radius = dp(62).toFloat()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    active = true
                    probeStarted = false
                    centerX = e.x
                    centerY = e.y
                    knobX = e.x
                    knobY = e.y
                    recordEvent("CUBUK: dokunuldu x=${e.x.toInt()} y=${e.y.toInt()}")
                }
                MotionEvent.ACTION_MOVE -> if (active) {
                    var dx = e.x - centerX
                    var dy = e.y - centerY
                    val dist = hypot(dx, dy)
                    if (dist > radius) {
                        dx *= radius / dist
                        dy *= radius / dist
                    }
                    knobX = centerX + dx
                    knobY = centerY + dy
                    val nx = dx / radius
                    val ny = dy / radius
                    val magnitude = hypot(nx, ny)
                    if (!probeStarted && magnitude >= 0.30f) {
                        probeStarted = true
                        requestEngineProbe("CUBUK_BASLA")
                    }
                    if (analogMovementMode) {
                        val fwd = if (magnitude >= 0.30f) -ny else 0f
                        val side = if (magnitude >= 0.30f) nx else 0f
                        SDLOpenTouch.fnafMove(fwd, side)
                        analogSent = true
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastAnalogLogMs >= 300) {
                            recordEvent("CUBUK ANALOG: ileri=${(fwd * 100).toInt()}% yan=${(side * 100).toInt()}%")
                            lastAnalogLogMs = now
                        }
                    } else {
                        val wanted = mutableSetOf<Int>()
                        if (magnitude >= 0.30f) {
                            if (ny < -0.35f) wanted.add(KeyEvent.KEYCODE_W)
                            if (ny > 0.35f) wanted.add(KeyEvent.KEYCODE_S)
                            if (nx < -0.35f) wanted.add(KeyEvent.KEYCODE_A)
                            if (nx > 0.35f) wanted.add(KeyEvent.KEYCODE_D)
                        }
                        for (key in pressed.toList()) {
                            if (key !in wanted) {
                                SDLActivity.onNativeKeyUp(key)
                                pressed.remove(key)
                                recordEvent("CUBUK SDL TUS YUKARI: ${KeyEvent.keyCodeToString(key)}")
                            }
                        }
                        for (key in wanted) {
                            if (key !in pressed) {
                                SDLActivity.onNativeKeyDown(key)
                                pressed.add(key)
                                recordEvent("CUBUK SDL TUS ASAGI: ${KeyEvent.keyCodeToString(key)}")
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopAll(if (e.actionMasked == MotionEvent.ACTION_CANCEL) "IPTAL" else "BIRAKILDI")
                }
            }
            invalidate()
            return true
        }
    }

    companion object {
        // PORT_ACT kodlari (Clibs_OpenTouch/port_act_defs.h)
        private const val ACT_SPEED = 10
        private const val ACT_USE = 11
        private const val ACT_JUMP = 12
        private const val ACT_ATTACK = 13
        private const val ACT_CROUCH = 15 // PORT_ACT_DOWN -> Button_Crouch
        private const val ACT_ALT_ATTACK = 64
    }
}
