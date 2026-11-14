package dev.ghazi.igfocus.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.ghazi.igfocus.LOG_TAG
import dev.ghazi.igfocus.R
import dev.ghazi.igfocus.inspector.CaptureLabels
import kotlin.math.abs

/**
 * The Inspector's floating Capture button and its label picker, drawn over Instagram as
 * accessibility overlays. Neither takes focus, so Instagram keeps the keyboard and Back.
 * Call from the main thread only.
 */
class CaptureOverlay(
    private val service: AccessibilityService,
    private val listener: Listener,
) {
    interface Listener {
        /** The Capture button was tapped. Return false if there is nothing to capture. */
        fun onCaptureRequested(): Boolean

        fun onLabelChosen(label: String)
    }

    // Fetched on first use: the service's window token exists only once it is connected.
    private val windowManager by lazy { service.getSystemService(WindowManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private val restoreButtonText = Runnable { button?.setText(R.string.capture_button) }

    private var button: TextView? = null
    private var picker: View? = null

    /** Where the user dragged the button to, kept while the service lives. */
    private var buttonOffsetY = 0

    val isShowing: Boolean get() = button != null || picker != null

    fun show() {
        if (!isShowing) addButton()
    }

    fun hide() {
        handler.removeCallbacks(restoreButtonText)
        button = remove(button)
        picker = remove(picker)
    }

    /** Puts the button back on top after a cover was added over it. An open picker is left alone. */
    fun raise() {
        if (button == null || picker != null) return
        handler.removeCallbacks(restoreButtonText)
        button = remove(button)
        addButton()
    }

    @SuppressLint("ClickableViewAccessibility") // performClick() is called for taps below.
    private fun addButton() {
        val view = TextView(service).apply {
            setText(R.string.capture_button)
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(PANEL_COLOR)
                cornerRadius = dp(20).toFloat()
            }
            setOnClickListener { onButtonTapped() }
        }
        val params = overlayParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(8)
            y = buttonOffsetY
        }

        // Drag up or down to move the button off whatever it covers; a still press is a tap.
        val slop = ViewConfiguration.get(service).scaledTouchSlop
        var downRawY = 0f
        var startY = 0
        var dragging = false
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawY = event.rawY
                    startY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dy) > slop) dragging = true
                    if (dragging) {
                        params.y = startY + dy
                        update(v, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (dragging) buttonOffsetY = params.y else v.performClick()
            }
            true
        }

        if (add(view, params)) button = view
    }

    private fun onButtonTapped() {
        if (!listener.onCaptureRequested()) {
            flash(R.string.capture_nothing)
            return
        }
        handler.removeCallbacks(restoreButtonText)
        button = remove(button)
        addPicker()
    }

    private fun addPicker() {
        val column = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        column.addView(pickerRow(service.getString(R.string.picker_title), TITLE_COLOR, null))
        for (label in CaptureLabels.ALL) {
            column.addView(pickerRow(label, Color.WHITE) {
                listener.onLabelChosen(label)
                closePicker()
                flash(R.string.capture_saved)
            })
        }
        column.addView(pickerRow(service.getString(R.string.cancel), TITLE_COLOR) { closePicker() })

        val scroll = ScrollView(service).apply {
            background = GradientDrawable().apply {
                setColor(PANEL_COLOR)
                val r = dp(16).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            addView(column)
        }
        // The lower part of the screen, so the screen being labelled stays visible above it.
        val height = (service.resources.displayMetrics.heightPixels * 0.55f).toInt()
        val params = overlayParams(MATCH_PARENT, height).apply { gravity = Gravity.BOTTOM }
        if (add(scroll, params)) picker = scroll else addButton()
    }

    private fun closePicker() {
        picker = remove(picker)
        addButton()
    }

    private fun pickerRow(text: String, color: Int, onClick: (() -> Unit)?) =
        TextView(service).apply {
            this.text = text
            setTextColor(color)
            textSize = 16f
            setPadding(dp(24), dp(12), dp(24), dp(12))
            if (onClick != null) setOnClickListener { onClick() }
        }

    private fun flash(textRes: Int) {
        button?.setText(textRes)
        handler.removeCallbacks(restoreButtonText)
        handler.postDelayed(restoreButtonText, FLASH_MS)
    }

    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // Not focusable also lets touches outside the overlay pass through to Instagram.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    )

    private fun add(view: View, params: WindowManager.LayoutParams): Boolean = try {
        windowManager.addView(view, params)
        true
    } catch (e: Exception) {
        Log.e(LOG_TAG, "Could not show the capture overlay", e)
        false
    }

    private fun update(view: View, params: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Could not move the capture button", e)
        }
    }

    /** Removes [view] if present. Always returns null, to clear the field that held it. */
    private fun <T : View> remove(view: T?): T? {
        if (view != null) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Could not remove a capture overlay view", e)
            }
        }
        return null
    }

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()

    private companion object {
        const val FLASH_MS = 1500L
        const val PANEL_COLOR = 0xF0202124.toInt()
        const val TITLE_COLOR = 0xFFB0B3B8.toInt()
    }
}
