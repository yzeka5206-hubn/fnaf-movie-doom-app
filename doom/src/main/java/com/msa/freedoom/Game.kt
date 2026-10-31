package com.msa.freedoom

import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.libsdl.app.SDLOpenTouch
import org.libsdl.app2012.SDLActivity
import kotlin.math.hypot
import kotlin.math.max
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
                    v.alpha = 0.6f
                    onDown()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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

            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        pid = e.getPointerId(0)
                        lastX = e.x
                        lastY = e.y
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
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pid = -1
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

        addContentView(
            root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    /** Dinamik (basilan yerde beliren) analog cubuk. */
    private inner class JoystickView(ctx: android.content.Context) : View(ctx) {
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
        private val basili = mutableSetOf<Int>()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = if (active) centerX else width / 2f
            val cy = if (active) centerY else height / 2f
            val r = dp(62).toFloat()
            canvas.drawCircle(cx, cy, r, fillPaint)
            canvas.drawCircle(cx, cy, r, basePaint)
            val kx = if (active) knobX else cx
            val ky = if (active) knobY else cy
            canvas.drawCircle(kx, ky, dp(27).toFloat(), knobPaint)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val radius = dp(62).toFloat()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    active = true
                    centerX = e.x
                    centerY = e.y
                    knobX = e.x
                    knobY = e.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (active) {
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
                        val istiyor = mutableSetOf<Int>()
                        if (hypot(nx, ny) >= 0.30f) {
                            if (ny < -0.35f) istiyor.add(KeyEvent.KEYCODE_W)
                            if (ny > 0.35f) istiyor.add(KeyEvent.KEYCODE_S)
                            if (nx < -0.35f) istiyor.add(KeyEvent.KEYCODE_A)
                            if (nx > 0.35f) istiyor.add(KeyEvent.KEYCODE_D)
                        }
                        for (k in basili.toList()) {
                            if (k !in istiyor) {
                                SDLActivity.onNativeKeyUp(k)
                                basili.remove(k)
                            }
                        }
                        for (k in istiyor) {
                            if (k !in basili) {
                                SDLActivity.onNativeKeyDown(k)
                                basili.add(k)
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    active = false
                    for (k in basili.toList()) SDLActivity.onNativeKeyUp(k)
                    basili.clear()
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
