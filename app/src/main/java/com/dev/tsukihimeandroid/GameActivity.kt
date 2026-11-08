package com.dev.tsukihimeandroid

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import com.google.androidgamesdk.GameActivity as AndroidGameActivity
import kotlin.math.hypot

private object ControlAction {
    const val ADVANCE = 0
    const val BACK = 1
    const val BACKLOG = 2
    const val AUTO = 3
    const val SKIP = 4
    const val QUICK_SAVE = 5
    const val QUICK_LOAD = 6
    const val OPEN_SAVE = 7
    const val OPEN_LOAD = 8
    const val CONFIGURATION = 9
    const val RETURN_TO_TITLE = 10
    const val GESTURES_OFF = 11
    const val GESTURES_ON = 12
}

/**
 * GameActivity host for Winit's Android GameActivity backend.
 * The Rust renderer stays in its SurfaceView; this Android View overlay is layered above it.
 * Buttons call a small JNI bridge; the native side forwards actions onto Winit's event-loop thread.
 */
class GameActivity : AndroidGameActivity() {
    private val prefs by lazy { getSharedPreferences("android_controls", MODE_PRIVATE) }
    private val controls = linkedMapOf<String, TextView>()
    private lateinit var overlay: ControlOverlay
    private lateinit var settingsButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

        // Always consume system Back at the game root so an accidental edge swipe cannot close the game.
        // When the user enables both gesture switches, route Back into Knife's normal B/cancel action.

        window.decorView.post { installOverlay() }
    }

    @Deprecated("Handled by in-game controls")
    override fun onBackPressed() {
        if (prefs.getBoolean("edge_back", true)) {
            sendControl(ControlAction.BACK)
        }
    }
    private fun installOverlay() {
        if (::overlay.isInitialized) return
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        overlay = ControlOverlay(this)
        content.addView(overlay, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        addControl("auto", "AUTO", 0.015f, 0.835f) { sendControl(ControlAction.AUTO) }
        addControl("skip", "SKIP", 0.085f, 0.835f) { sendControl(ControlAction.SKIP) }
        addControl("save", "SAVE", 0.155f, 0.835f) { sendControl(ControlAction.QUICK_SAVE) }
        addControl("load", "LOAD", 0.225f, 0.835f) { sendControl(ControlAction.QUICK_LOAD) }
        addControl("advance", "NEXT", 0.675f, 0.835f) { sendControl(ControlAction.ADVANCE) }
        addControl("back", "BACK", 0.745f, 0.835f) { sendControl(ControlAction.BACK) }
        addControl("backlog", "BACKLOG", 0.815f, 0.835f) { sendControl(ControlAction.BACKLOG) }
        addControl("menu", "MENU", 0.885f, 0.835f) { showGameMenu() }

        settingsButton = makeButton("⚙", true).apply {
            contentDescription = "Controls settings"
            setOnClickListener { showControlsSettings() }
        }
        overlay.addView(settingsButton, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(12)
            marginEnd = dp(12)
        })
        overlay.post {
            applyPreferences()
            syncGesturePreference()
        }
    }

    private fun addControl(id: String, label: String, defaultX: Float, defaultY: Float, action: () -> Unit) {
        val view = makeButton(label)
        controls[id] = view
        view.setOnTouchListener(object : View.OnTouchListener {
            private var downRawX = 0f
            private var downRawY = 0f
            private var lastRawX = 0f
            private var lastRawY = 0f
            private var downAt = 0L
            private var dragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        downAt = event.eventTime
                        dragging = false
                        v.isPressed = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dxFromDown = event.rawX - downRawX
                        val dyFromDown = event.rawY - downRawY
                        if (!dragging && event.eventTime - downAt >= 350L &&
                            hypot(dxFromDown.toDouble(), dyFromDown.toDouble()) >= dp(8).toDouble()) {
                            dragging = true
                        }
                        if (dragging) {
                            val parent = v.parent as? View ?: return true
                            v.x = (v.x + event.rawX - lastRawX).coerceIn(0f, (parent.width - v.width).coerceAtLeast(0).toFloat())
                            v.y = (v.y + event.rawY - lastRawY).coerceIn(0f, (parent.height - v.height).coerceAtLeast(0).toFloat())
                        }
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.isPressed = false
                        if (dragging) {
                            savePosition(id, v)
                        } else {
                            action()
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        if (dragging) savePosition(id, v)
                        return true
                    }
                }
                return true
            }
        })
        overlay.addView(view, FrameLayout.LayoutParams(dp(buttonSize()), dp(buttonSize() * 0.76f).toInt(), Gravity.TOP or Gravity.START))
        view.tag = floatArrayOf(defaultX, defaultY)
    }

    private fun makeButton(label: String, settings: Boolean = false) = TextView(this).apply {
        text = label
        contentDescription = label
        textSize = if (settings) 20f else 9f
        setTextColor(0xFFF0E7D7.toInt())
        typeface = Typeface.create("serif", Typeface.BOLD)
        letterSpacing = if (settings) 0f else 0.08f
        gravity = Gravity.CENTER
        maxLines = 1
        isClickable = true
        isFocusable = false
        isFocusableInTouchMode = false
        background = controlButtonDrawable(settings)
        elevation = dp(3).toFloat()
        alpha = prefs.getInt("opacity", 76).coerceIn(20, 100) / 100f
    }

    /** Restrained moonlit-oxblood glass: ink-dark fill, warm ivory lettering and a muted silver-gold edge. */
    private fun controlButtonDrawable(round: Boolean = false) = StateListDrawable().apply {
        fun shape(top: Int, bottom: Int, stroke: Int) = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)
        ).apply {
            shape = if (round) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
            cornerRadius = dp(if (round) 24 else 8).toFloat()
            setStroke(dp(1), stroke)
        }
        addState(
            intArrayOf(android.R.attr.state_pressed),
            shape(0xF06A2638.toInt(), 0xF01B101B.toInt(), 0xFFE0C18C.toInt())
        )
        addState(
            intArrayOf(),
            shape(0xD92A202B.toInt(), 0xE60C1018.toInt(), 0x99C7B99B.toInt())
        )
    }

    private fun showGameMenu() {
        val dialog = AlertDialog.Builder(this).create()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xF027202B.toInt(), 0xF00B1018.toInt())
            ).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0xB8C4B18F.toInt())
            }
        }
        val heading = TextView(this).apply {
            text = "SYSTEM MENU"
            textSize = 15f
            letterSpacing = 0.16f
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(0xFFF0E7D7.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        panel.addView(heading, LinearLayout.LayoutParams(-1, -2))
        val entries = listOf(
            "RESUME" to { dialog.dismiss() },
            "BACK" to { dialog.dismiss(); sendControl(ControlAction.BACK) },
            "SAVE SCREEN" to { dialog.dismiss(); sendControl(ControlAction.OPEN_SAVE) },
            "LOAD SCREEN" to { dialog.dismiss(); sendControl(ControlAction.OPEN_LOAD) },
            "CONFIGURATION" to { dialog.dismiss(); sendControl(ControlAction.CONFIGURATION) },
            "RETURN TO TITLE" to { dialog.dismiss(); confirmReturnToTitle() },
            "EXIT GAME" to { dialog.dismiss(); confirmExit() }
        )
        entries.forEach { (label, action) ->
            val row = makeButton(label).apply {
                textSize = 11f
                setOnClickListener { action() }
            }
            panel.addView(row, LinearLayout.LayoutParams(-1, dp(42)).apply {
                bottomMargin = dp(7)
            })
        }
        dialog.setView(panel)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(dp(310), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    private fun confirmReturnToTitle() {
        AlertDialog.Builder(this)
            .setTitle("Return to title?")
            .setMessage("Return to the game's title screen?")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("RETURN") { _, _ -> sendControl(ControlAction.RETURN_TO_TITLE) }
            .show()
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("Exit game?")
            .setMessage("Return to the Tsukihime launcher?")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("EXIT") { _, _ -> finish() }
            .show()
    }

    private fun showControlsSettings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
            setBackgroundColor(0xFF151923.toInt())
        }
        content.addView(settingSection("CONTROLS"))
        val dialog = AlertDialog.Builder(this)
            .setView(android.widget.ScrollView(this).apply { addView(content) })
            .setPositiveButton("DONE", null)
            .create()

        fun check(label: String, key: String, default: Boolean) {
            content.addView(CheckBox(this).apply {
                text = label
                textSize = 12f
                typeface = Typeface.create("serif", Typeface.NORMAL)
                setTextColor(0xFFE9E0D1.toInt())
                buttonTintList = ColorStateList.valueOf(0xFFC8B58E.toInt())
                setPadding(0, dp(5), 0, dp(5))
                isChecked = prefs.getBoolean(key, default)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(key, checked).apply()
                    if (key == "gestures") syncGesturePreference()
                    applyPreferences()
                }
            })
        }
        check("Enable on-screen controls", "enabled", true)
        check("Show Advance button", "show_advance", true)
        check("Show Back button", "show_back", true)
        check("Show Backlog button", "show_backlog", true)
        check("Show Menu button", "show_menu", true)
        check("Show Auto button", "show_auto", true)
        check("Show Skip button", "show_skip", true)
        check("Show Save button", "show_save", true)
        check("Show Load button", "show_load", true)
        check("Enable in-game swipe gestures", "gestures", true)
        check("Use Android system Back gesture / button", "edge_back", true)

        content.addView(settingSection("APPEARANCE"))
        val opacityLabel = settingLabel("Button opacity: ${prefs.getInt("opacity", 76)}%")
        content.addView(opacityLabel)
        content.addView(SeekBar(this).apply {
            progressTintList = ColorStateList.valueOf(0xFFC8B58E.toInt())
            thumbTintList = ColorStateList.valueOf(0xFFE6D4AE.toInt())
            max = 80
            progress = prefs.getInt("opacity", 76).coerceIn(20, 100) - 20
            setOnSeekBarChangeListener(seekListener { value ->
                val opacity = value + 20
                prefs.edit().putInt("opacity", opacity).apply()
                opacityLabel.text = "Button opacity: $opacity%"
                applyPreferences()
            })
        })

        val sizeLabel = settingLabel("Button size: ${buttonSize()} dp")
        content.addView(sizeLabel)
        content.addView(SeekBar(this).apply {
            progressTintList = ColorStateList.valueOf(0xFFC8B58E.toInt())
            thumbTintList = ColorStateList.valueOf(0xFFE6D4AE.toInt())
            max = 44
            progress = buttonSize() - 44
            setOnSeekBarChangeListener(seekListener { value ->
                val size = value + 44
                prefs.edit().putInt("size", size).apply()
                sizeLabel.text = "Button size: $size dp"
                applyPreferences()
            })
        })
        content.addView(settingSection("LAYOUT"))
        content.addView(settingLabel("Long-press a control, then drag it to reposition it."))
        content.addView(makeButton("RESET BUTTON POSITIONS").apply {
            textSize = 10f
            setOnClickListener {
                val edit = prefs.edit()
                listOf("auto", "skip", "save", "load", "advance", "back", "backlog", "menu").forEach { id ->
                    edit.remove("pos_${id}_x").remove("pos_${id}_y")
                }
                edit.apply()
                applyPreferences()
            }
        }, LinearLayout.LayoutParams(-1, dp(42)))
        dialog.setOnDismissListener { applyPreferences() }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xF027202B.toInt(), 0xF00B1018.toInt())
            ).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0xB8C4B18F.toInt())
            })
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFFE6D4AE.toInt())
        }
        dialog.show()
    }

    private fun settingSection(value: String) = TextView(this).apply {
        text = value
        setTextColor(0xFFD0B98C.toInt())
        textSize = 10f
        letterSpacing = 0.18f
        typeface = Typeface.create("serif", Typeface.BOLD)
        setPadding(0, dp(14), 0, dp(3))
    }

    private fun settingLabel(value: String) = TextView(this).apply {
        text = value
        setTextColor(0xFFE9E0D1.toInt())
        typeface = Typeface.create("serif", Typeface.NORMAL)
        textSize = 12f
        setPadding(0, dp(10), 0, dp(3))
    }

    private fun seekListener(onChanged: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChanged(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun applyPreferences() {
        if (!::overlay.isInitialized) return
        val enabled = prefs.getBoolean("enabled", true)
        val visibility = mapOf(
            "advance" to "show_advance", "back" to "show_back", "backlog" to "show_backlog",
            "menu" to "show_menu", "auto" to "show_auto", "skip" to "show_skip",
            "save" to "show_save", "load" to "show_load"
        )
        val alpha = prefs.getInt("opacity", 76).coerceIn(20, 100) / 100f
        val size = buttonSize()
        controls.forEach { (id, view) ->
            view.visibility = if (enabled && prefs.getBoolean(visibility.getValue(id), true)) View.VISIBLE else View.GONE
            view.alpha = alpha
            val params = view.layoutParams as FrameLayout.LayoutParams
            params.width = dp(size)
            params.height = dp(size * 0.76f).toInt()
            view.layoutParams = params
            val savedX = prefs.getFloat("pos_${id}_x", (view.tag as? FloatArray)?.getOrNull(0) ?: 0.5f)
            val savedY = prefs.getFloat("pos_${id}_y", (view.tag as? FloatArray)?.getOrNull(1) ?: 0.8f)
            view.x = (savedX * (overlay.width - view.width).coerceAtLeast(0)).coerceIn(0f, (overlay.width - view.width).coerceAtLeast(0).toFloat())
            view.y = (savedY * (overlay.height - view.height).coerceAtLeast(0)).coerceIn(0f, (overlay.height - view.height).coerceAtLeast(0).toFloat())
        }
        if (::settingsButton.isInitialized) {
            settingsButton.alpha = alpha
            settingsButton.visibility = View.VISIBLE
        }
    }

    private fun savePosition(id: String, view: View) {
        val maxX = (overlay.width - view.width).coerceAtLeast(1)
        val maxY = (overlay.height - view.height).coerceAtLeast(1)
        prefs.edit()
            .putFloat("pos_${id}_x", (view.x / maxX).coerceIn(0f, 1f))
            .putFloat("pos_${id}_y", (view.y / maxY).coerceIn(0f, 1f))
            .apply()
    }

    private fun buttonSize() = prefs.getInt("size", 58).coerceIn(44, 88)

    private fun sendControl(action: Int) {
        nativeControl(action)
    }

    @Keep
    private external fun nativeControl(action: Int)

    private fun syncGesturePreference() {
        sendControl(if (prefs.getBoolean("gestures", true)) ControlAction.GESTURES_ON else ControlAction.GESTURES_OFF)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = value * resources.displayMetrics.density

    private inner class ControlOverlay(context: android.content.Context) : FrameLayout(context) {
        init {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
            isFocusable = false
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0 && controls.isNotEmpty()) post { applyPreferences() }
        }
    }
}
