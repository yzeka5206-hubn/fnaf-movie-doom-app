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
import android.os.Handler
import android.os.Looper
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
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.libsdl.app.SDLOpenTouch
import org.libsdl.app2012.SDLActivity
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

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
    private val buildMarker = "FNAF_DIAG_V7_2026"
    private val diagnosticEvents = ArrayDeque<String>()
    private var analogMovementMode = false
    private lateinit var joystick: JoystickView
    private lateinit var logPanel: LinearLayout
    private lateinit var logText: TextView
    private var probeSerial = 0

    private val watchState = LatchingSwitch()
    private val crouchState = LatchingSwitch()
    private val runState = LatchingSwitch()
    private var watchButton: TextView? = null
    private var crouchButton: TextView? = null
    private var runButton: TextView? = null
    private var keyboardButton: TextView? = null
    private var keyboardPanel: LinearLayout? = null
    private var keyboardOpen = false
    private var keyboardShift = false
    private var keyboardSymbols = false
    private var gameplayControls: List<View> = emptyList()
    private var utilityControls: List<View> = emptyList()
    private var uiMode = 0 // 0 gameplay, 1 character selector, 2 engine menu
    private val uiSession = Random.nextInt(1, Int.MAX_VALUE)
    @Volatile private var monitorRunning = false
    private var uiMonitor: ScheduledExecutorService? = null
    private var reverseLookX = true
    private var reverseLookY = true
    private val heldOverlayKeys = mutableSetOf<Int>()
    private val heldActions = mutableSetOf<Int>()
    private val inputHandler = Handler(Looper.getMainLooper())
    private val flashlightRelease = Runnable {
        setHeldAction(ACT_ATTACK, false)
        recordEvent("FENER: tek atim tamamlandi")
    }

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
        val controls = getSharedPreferences("fnaf_controls_v7", Context.MODE_PRIVATE)
        reverseLookX = controls.getBoolean("reverse_x", true)
        reverseLookY = controls.getBoolean("reverse_y", true)
        recordEvent("Oyun aktivitesi acildi; surum=${BuildConfig.VERSION_NAME}")
        buildTouchOverlay()
        startUiStateMonitor()
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

    override fun onPause() {
        releaseAllOverlayInputs("UYKU")
        setKeyboardOpen(false)
        super.onPause()
    }

    override fun onDestroy() {
        monitorRunning = false
        uiMonitor?.shutdownNow()
        uiMonitor = null
        releaseAllOverlayInputs("KAPANIS")
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

    private fun setOverlayKey(keyCode: Int, down: Boolean) {
        if (down && heldOverlayKeys.add(keyCode)) SDLActivity.onNativeKeyDown(keyCode)
        if (!down && heldOverlayKeys.remove(keyCode)) SDLActivity.onNativeKeyUp(keyCode)
    }

    private fun keyTap(keyCode: Int): Pair<() -> Unit, () -> Unit> =
        Pair({ setOverlayKey(keyCode, true) }, { setOverlayKey(keyCode, false) })

    private fun setHeldAction(action: Int, down: Boolean) {
        if (down && heldActions.add(action)) SDLOpenTouch.fnafAction(1, action)
        if (!down && heldActions.remove(action)) SDLOpenTouch.fnafAction(0, action)
    }

    private fun updateLatchButtons() {
        watchButton?.apply {
            text = if (watchState.active) "SAAT\nACIK" else "SAAT"
            setTextColor(if (watchState.active) Color.YELLOW else Color.WHITE)
        }
        crouchButton?.apply {
            text = if (crouchState.active) "EGIL\nACIK" else "EGIL"
            setTextColor(if (crouchState.active) Color.YELLOW else Color.WHITE)
        }
        runButton?.apply {
            text = if (runState.active) "KOS\nACIK" else "KOS"
            setTextColor(if (runState.active) Color.YELLOW else Color.WHITE)
        }
    }

    private fun pulseFlashlight() {
        // Fire already toggles the light in the game. A short pulse prevents a
        // long finger press from repeatedly toggling it via weapon auto-fire.
        if (ACT_ATTACK in heldActions) return
        setHeldAction(ACT_ATTACK, true)
        inputHandler.postDelayed(flashlightRelease, 150L)
    }

    private fun releaseGameplayInputs(reason: String) {
        if (::joystick.isInitialized) joystick.stopAll(reason)
        inputHandler.removeCallbacks(flashlightRelease)
        for (action in heldActions.toList()) setHeldAction(action, false)
        for (code in intArrayOf(KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_SHIFT_LEFT)) {
            setOverlayKey(code, false)
        }
        watchState.clear()
        crouchState.clear()
        runState.clear()
        updateLatchButtons()
        recordEvent("OYUN TUSLARI: birakildi ($reason)")
    }

    private fun releaseAllOverlayInputs(reason: String) {
        releaseGameplayInputs(reason)
        for (code in heldOverlayKeys.toList()) setOverlayKey(code, false)
    }

    private fun startUiStateMonitor() {
        val base = intent.getStringExtra("game_path") ?: return
        val reader = FnafUiStateReader(File(File(base, "user_files"), "gzdoom_log.txt"), uiSession)
        val worker = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "Fnaf-ui-state").apply { isDaemon = true }
        }
        uiMonitor = worker
        monitorRunning = true
        var lastPing = 0L
        worker.scheduleWithFixedDelay({
            try {
                if (monitorRunning && SDLOpenTouch.fnafCanQueryEngine()) {
                    val now = SystemClock.elapsedRealtime()
                    if (lastPing == 0L || now - lastPing >= 2000L) {
                        SDLOpenTouch.fnafCommand("interfaceevent FnafTouchPing $uiSession")
                        lastPing = now
                    }
                    val mode = reader.poll()
                    if (mode != null) inputHandler.post {
                        if (monitorRunning && !isFinishing && !isDestroyed) setGameUiMode(mode)
                    }
                }
            } catch (_: Exception) {
                // Missing/transient log access is retried; gameplay stays usable.
            } catch (_: LinkageError) {
                // The engine can exit between the readiness check and JNI call.
            }
        }, 0L, 150L, TimeUnit.MILLISECONDS)
    }

    private fun setGameUiMode(mode: Int) {
        if (mode !in 0..2 || mode == uiMode) return
        uiMode = mode
        if (mode != 0) releaseGameplayInputs("OYUN MENUSU")
        updateControlsVisibility()
        recordEvent("DOKUNMA EKRANI: $mode")
    }

    private fun updateControlsVisibility() {
        val visible = uiMode == 0 && !keyboardOpen
        (gameplayControls + utilityControls).forEach { it.visibility = if (visible) View.VISIBLE else View.GONE }
    }

    private fun sendDirectTouch(x: Float, y: Float, width: Int, height: Int, phase: Int) {
        if (!SDLOpenTouch.fnafCanQueryEngine()) return
        val nx = fnafTouchCoordinate(x, width)
        val ny = fnafTouchCoordinate(y, height)
        try {
            SDLOpenTouch.fnafCommand("interfaceevent FnafTouch $nx $ny $phase")
        } catch (_: Exception) {
            recordEvent("DOKUNMA KOPRUSU: motor kullanilamiyor")
        } catch (_: LinkageError) {
            recordEvent("DOKUNMA KOPRUSU: motor kapandi")
        }
    }

    private fun setKeyboardOpen(open: Boolean) {
        if (keyboardOpen == open) return
        keyboardOpen = open
        if (open) releaseGameplayInputs("KLAVYE")
        keyboardPanel?.visibility = if (open) View.VISIBLE else View.GONE
        keyboardButton?.apply {
            text = if (open) "KLAVYE\nACIK" else "KLAVYE"
            setTextColor(if (open) Color.YELLOW else Color.WHITE)
        }
        updateControlsVisibility()
        recordEvent("KLAVYE: ${if (open) "ACIK" else "KAPALI"}")
    }

    private fun saveLookDirections() {
        getSharedPreferences("fnaf_controls_v7", Context.MODE_PRIVATE).edit()
            .putBoolean("reverse_x", reverseLookX)
            .putBoolean("reverse_y", reverseLookY)
            .apply()
    }

    private fun buildTouchOverlay() {
        val root = FrameLayout(this)

        // Full-screen look area automatically becomes direct touch on menus.
        val look = object : View(this) {
            private var pid = -1
            private var lastX = 0f
            private var lastY = 0f
            private var startX = 0f
            private var startY = 0f
            private var moved = false
            private var menuGesture = false
            private var lastSendMs = 0L
            private var lastLogMs = 0L

            private fun finish(cancel: Boolean, x: Float, y: Float) {
                if (pid < 0) return
                if (menuGesture) {
                    sendDirectTouch(x, y, width, height, if (cancel) 3 else 2)
                } else if (!cancel && !moved) {
                    // The adapter checks the current screen; gameplay gets no click.
                    sendDirectTouch(x, y, width, height, 0)
                    sendDirectTouch(x, y, width, height, 2)
                }
                pid = -1
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        pid = e.getPointerId(0)
                        startX = e.x; startY = e.y
                        lastX = e.x; lastY = e.y
                        moved = false
                        menuGesture = uiMode != 0
                        lastSendMs = SystemClock.elapsedRealtime()
                        if (menuGesture) sendDirectTouch(e.x, e.y, width, height, 0)
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> Unit
                    MotionEvent.ACTION_MOVE -> {
                        val i = e.findPointerIndex(pid)
                        if (i >= 0) {
                            val x = e.getX(i); val y = e.getY(i)
                            val dx = x - lastX; val dy = y - lastY
                            lastX = x; lastY = y
                            if (hypot(x - startX, y - startY) > dp(8)) moved = true
                            val now = SystemClock.elapsedRealtime()
                            if (menuGesture) {
                                if (now - lastSendMs >= 35L) {
                                    sendDirectTouch(x, y, width, height, 1)
                                    lastSendMs = now
                                }
                            } else {
                                // Preserve the existing camera/axis fixes.
                                val cameraX = orientCameraDelta(dx, reverseLookX)
                                val cameraY = orientCameraDelta(dy, reverseLookY)
                                SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE, cameraX, cameraY, true)
                                SDLOpenTouch.fnafLook(cameraY * 0.0016f, cameraX * 0.00055f)
                                if (now - lastLogMs >= 300) {
                                    recordEvent("BAKIS: dx=${dx.toInt()} dy=${dy.toInt()}")
                                    lastLogMs = now
                                }
                            }
                        }
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        if (e.getPointerId(e.actionIndex) == pid) finish(false, e.getX(e.actionIndex), e.getY(e.actionIndex))
                    }
                    MotionEvent.ACTION_UP -> finish(false, e.x, e.y)
                    MotionEvent.ACTION_CANCEL -> finish(true, lastX, lastY)
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

        // 3) AL is E only: never send attack/flashlight from this button.
        val (alDown, alUp) = keyTap(KeyEvent.KEYCODE_E)
        val al = makeButton("AL", 84, alDown, alUp)
        place(root, al, Gravity.BOTTOM or Gravity.END, right = 30, bottom = 36)

        // The game's own Fire state toggles the flashlight on each short press.
        val fener = makeButton("FENER", 70, { pulseFlashlight() }, {})
        place(root, fener, Gravity.BOTTOM or Gravity.END, right = 122, bottom = 128)

        // Mouse2 is the game's watch. Keep alt-attack down until tapped again.
        val saat = makeButton("SAAT", 70, {
            val active = watchState.toggle()
            setHeldAction(ACT_ALT_ATTACK, active)
            updateLatchButtons()
            recordEvent("SAAT: ${if (active) "ACIK" else "KAPALI"}")
        }, {})
        watchButton = saat
        place(root, saat, Gravity.BOTTOM or Gravity.END, right = 214, bottom = 36)

        val zipla = makeButton("ZIPLA", 64,
            {
                setHeldAction(ACT_JUMP, true)
                setOverlayKey(KeyEvent.KEYCODE_SPACE, true)
            },
            {
                setHeldAction(ACT_JUMP, false)
                setOverlayKey(KeyEvent.KEYCODE_SPACE, false)
            })
        place(root, zipla, Gravity.BOTTOM or Gravity.END, right = 40, bottom = 140)

        // Toggle the existing crouch action instead of requiring a long hold.
        val egil = makeButton("EGIL", 64, {
            val active = crouchState.toggle()
            setHeldAction(ACT_CROUCH, active)
            updateLatchButtons()
            recordEvent("EGIL: ${if (active) "ACIK" else "KAPALI"}")
        }, {})
        crouchButton = egil
        place(root, egil, Gravity.BOTTOM or Gravity.END, right = 132, bottom = 36)

        val kos = makeButton("KOS", 64, {
            val active = runState.toggle()
            setHeldAction(ACT_SPEED, active)
            setOverlayKey(KeyEvent.KEYCODE_SHIFT_LEFT, active)
            updateLatchButtons()
            recordEvent("KOS: ${if (active) "ACIK" else "KAPALI"}")
        }, {})
        runButton = kos
        place(root, kos, Gravity.BOTTOM or Gravity.END, right = 44, bottom = 224)
        gameplayControls = listOf(stick, al, fener, saat, zipla, egil, kos)

        // 4) Ust sol: only MENU deliberately sends back/escape.
        val menu = makeButton("MENU", 58, {
            releaseAllOverlayInputs("MENU")
            SDLOpenTouch.fnafMenu()
        }, {})
        place(root, menu, Gravity.TOP or Gravity.START, left = 18, top = 14)

        // LOG only opens an app panel; it does not send ESC/ENTER to the game.
        val logBtn = makeButton("LOG", 58, { openLogPanel() }, {})
        place(root, logBtn, Gravity.TOP or Gravity.START, left = 88, top = 14)

        val keyboard = makeButton("KLAVYE", 58, { setKeyboardOpen(!keyboardOpen) }, {})
        keyboardButton = keyboard
        place(root, keyboard, Gravity.TOP or Gravity.START, left = 158, top = 14)

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
        utilityControls = listOf(menu, logBtn, enter, up, down)
        keyboardPanel = createEmbeddedKeyboard().also { panel ->
            panel.visibility = View.GONE
            root.addView(panel, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(200),
            ).apply { gravity = Gravity.BOTTOM })
        }

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

    /** App keyboard does not rely on the Android IME becoming visible. */
    private fun createEmbeddedKeyboard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
        setBackgroundColor(Color.argb(238, 10, 16, 22))
        isClickable = true
        populateKeyboard(this)
    }

    private fun populateKeyboard(panel: LinearLayout) {
        panel.removeAllViews()
        val letters = listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("q", "w", "e", "r", "t", "y", "u", "ı", "o", "p", "ğ", "ü"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ş", "i"),
            listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "ö", "ç", "⌫"),
            listOf("?123", "ESC", "←", "BOŞLUK", "→", "ENTER"),
        )
        val symbols = listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
            listOf("*", "\"", "'", ":", ";", "!", "?", "_", "=", ".", ","),
            listOf("ABC", "[", "]", "{", "}", "<", ">", "\\", "|", "⌫"),
            listOf("ABC", "ESC", "←", "BOŞLUK", "→", "ENTER"),
        )
        for (rowKeys in if (keyboardSymbols) symbols else letters) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            panel.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            for (key in rowKeys) {
                val shown = if (keyboardShift && !keyboardSymbols && key.length == 1 && key[0].isLetter()) {
                    key.uppercase(Locale.forLanguageTag("tr-TR"))
                } else key
                val keyView = TextView(this).apply {
                    text = shown
                    textSize = if (key == "BOŞLUK" || key == "ENTER") 12f else 14f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        setColor(if (key == "⇧" && keyboardShift) Color.rgb(82, 105, 118) else Color.rgb(42, 51, 60))
                        setStroke(dp(1), Color.rgb(93, 107, 116))
                        cornerRadius = dp(4).toFloat()
                    }
                    setOnClickListener {
                        when (key) {
                            "⇧" -> { keyboardShift = !keyboardShift; populateKeyboard(panel) }
                            "?123", "ABC" -> { keyboardSymbols = !keyboardSymbols; populateKeyboard(panel) }
                            "⌫" -> sendKeyboardKey(KeyEvent.KEYCODE_DEL)
                            "ESC" -> sendKeyboardKey(KeyEvent.KEYCODE_ESCAPE)
                            "←" -> sendKeyboardKey(KeyEvent.KEYCODE_DPAD_LEFT)
                            "→" -> sendKeyboardKey(KeyEvent.KEYCODE_DPAD_RIGHT)
                            "ENTER" -> sendKeyboardKey(KeyEvent.KEYCODE_ENTER)
                            "BOŞLUK" -> sendKeyboardText(" ")
                            else -> sendKeyboardText(shown)
                        }
                    }
                }
                val weight = if (key == "BOŞLUK") 3f else 1f
                row.addView(keyView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
                    setMargins(dp(1), dp(1), dp(1), dp(1))
                })
            }
        }
    }

    private fun sendKeyboardKey(code: Int) {
        SDLActivity.onNativeKeyDown(code)
        SDLActivity.onNativeKeyUp(code)
    }

    private fun sendKeyboardText(text: String) {
        if (text.length == 1) {
            val c = text[0].lowercaseChar()
            val code = when (c) {
                in 'a'..'z' -> KeyEvent.KEYCODE_A + (c - 'a')
                in '0'..'9' -> KeyEvent.KEYCODE_0 + (c - '0')
                ' ' -> KeyEvent.KEYCODE_SPACE
                else -> 0
            }
            if (code != 0) sendKeyboardKey(code)
        }
        SDLActivity.fnafCommitText(text)
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
        addToolbarButton(if (reverseLookX) "BAKIS X: 1" else "BAKIS X: 2") { button ->
            reverseLookX = !reverseLookX
            button.text = if (reverseLookX) "BAKIS X: 1" else "BAKIS X: 2"
            saveLookDirections()
            recordEvent("BAKIS X: ${if (reverseLookX) 1 else 2}")
            refreshLogPanel()
        }
        addToolbarButton(if (reverseLookY) "BAKIS Y: 1" else "BAKIS Y: 2") { button ->
            reverseLookY = !reverseLookY
            button.text = if (reverseLookY) "BAKIS Y: 1" else "BAKIS Y: 2"
            saveLookDirections()
            recordEvent("BAKIS Y: ${if (reverseLookY) 1 else 2}")
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
        appendLine("Dokunma ekran durumu: $uiMode | Bakis X: ${if (reverseLookX) 1 else 2} Y: ${if (reverseLookY) 1 else 2}")
        appendLine("Saat: ${watchState.active} | Egil: ${crouchState.active} | Kos: ${runState.active} | Klavye: $keyboardOpen")
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
        private const val ACT_JUMP = 12
        private const val ACT_ATTACK = 13
        private const val ACT_CROUCH = 15 // PORT_ACT_DOWN -> Button_Crouch
        private const val ACT_ALT_ATTACK = 64
    }
}
