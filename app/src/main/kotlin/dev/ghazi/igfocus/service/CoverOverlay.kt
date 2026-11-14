package dev.ghazi.igfocus.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.ghazi.igfocus.LOG_TAG
import dev.ghazi.igfocus.R
import dev.ghazi.igfocus.policy.Box
import dev.ghazi.igfocus.policy.Cover

/**
 * Opaque covers over parts of Instagram, drawn as accessibility overlays. A cover absorbs every
 * touch inside it; touches outside it reach Instagram. It never takes focus, so Back and the
 * keyboard stay with Instagram. Call from the main thread only.
 *
 * Covers are styled like Instagram's own surfaces (its dark blue-grey or white, grey caption, a
 * filled secondary button), following the phone's dark mode, which Instagram follows by default.
 */
class CoverOverlay(
    private val service: AccessibilityService,
    private val onOpenMessages: () -> Unit,
    /** A touch landed outside every cover (the Stories row, the top bar, the tabs). No position. */
    private val onOutsideTouch: () -> Unit,
    /** A finger went down on a tab-button patch. */
    private val onTabDown: (Cover) -> Unit,
    /** A finger lifted on a tab-button patch: the tap to pass on to the tab. */
    private val onTabUp: (Cover) -> Unit,
    /** A content cover went up (true) or the last one came down (false). */
    private val onContentShown: (Boolean) -> Unit,
) {
    // Fetched on first use: the service's window token exists only once it is connected.
    private val windowManager by lazy { service.getSystemService(WindowManager::class.java) }
    private val shown = mutableMapOf<Cover, Pair<View, WindowManager.LayoutParams>>()

    val isShowing: Boolean get() = shown.isNotEmpty()

    /** Whether a cover over Instagram content (not just the Reels-button patch) is up. */
    val isShowingContent: Boolean get() = shown.keys.any { it.hidesContent }

    /**
     * Makes the covers on screen exactly [wanted]: adds missing ones, moves ones whose place
     * changed, removes the rest. Returns the covers that were newly added.
     */
    fun reconcile(wanted: Map<Cover, Box>): Set<Cover> {
        for (cover in shown.keys - wanted.keys) remove(cover)
        val added = mutableSetOf<Cover>()
        for ((cover, box) in wanted) {
            val current = shown[cover]
            if (current == null) {
                if (add(cover, box)) added += cover
            } else {
                move(current.first, current.second, box)
            }
        }
        return added
    }

    fun hideAll() {
        for (cover in shown.keys.toList()) remove(cover)
    }

    fun hide(cover: Cover) = remove(cover)

    /** Puts [cover] up at [box] if it is not already showing. Returns whether it was added. */
    fun show(cover: Cover, box: Box): Boolean = cover !in shown && add(cover, box)

    /** The top edge of [cover] on screen, or null if it is not showing. */
    fun topOf(cover: Cover): Int? = shown[cover]?.second?.y

    /** Where [cover] is on screen, or null if it is not showing. */
    fun boxOf(cover: Cover): Box? = shown[cover]?.second?.let { Box(it.x, it.y, it.x + it.width, it.y + it.height) }

    private fun add(cover: Cover, box: Box): Boolean {
        val view = build(cover)
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        // Only the content covers watch outside touches. The tab-bar patches are up on the DM
        // inbox too, and watching there would trigger fast scans on every tap, keyboard included.
        if (cover.hidesContent) flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        val params = WindowManager.LayoutParams(
            box.width,
            box.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            // The tab-button patches are see-through: Instagram's own button shows beneath.
            if (cover.raises != null) PixelFormat.TRANSLUCENT else PixelFormat.OPAQUE,
        ).apply {
            // Place in raw screen pixels, matching getBoundsInScreen(): no insets, cutout allowed.
            gravity = Gravity.TOP or Gravity.START
            x = box.left
            y = box.top
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        return try {
            val hadContent = isShowingContent
            windowManager.addView(view, params)
            shown[cover] = view to params
            if (isShowingContent != hadContent) onContentShown(true)
            true
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Could not show the $cover cover", e)
            false
        }
    }

    private fun move(view: View, params: WindowManager.LayoutParams, box: Box) {
        if (params.x == box.left && params.y == box.top && params.width == box.width && params.height == box.height) return
        params.x = box.left
        params.y = box.top
        params.width = box.width
        params.height = box.height
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Could not move a cover", e)
        }
    }

    private fun remove(cover: Cover) {
        val hadContent = isShowingContent
        val view = shown.remove(cover)?.first ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Could not remove the $cover cover", e)
        }
        if (isShowingContent != hadContent) onContentShown(false)
    }

    @SuppressLint("ClickableViewAccessibility") // Only ACTION_OUTSIDE is handled; taps fall through.
    private fun build(cover: Cover): View {
        val night = (service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val surface = if (night) DARK_SURFACE else Color.WHITE
        val tabBar = if (night) DARK_TAB_BAR else Color.WHITE
        val caption = if (night) 0xFFA8A8A8.toInt() else 0xFF737373.toInt()
        val buttonFill = if (night) 0xFF363636.toInt() else 0xFFEFEFEF.toInt()
        val buttonText = if (night) Color.WHITE else Color.BLACK

        if (cover == Cover.REELS_TAB_BUTTON) {
            // A blank patch the colour of the tab bar, absorbing taps on the hidden button.
            return FrameLayout(service).apply {
                setBackgroundColor(tabBar)
                isClickable = true
            }
        }
        if (cover.raises != null) return tabButtonPatch(cover)

        val column = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        // Wrap-content, not the default match-parent: in a wrap-content column a match-parent
        // child does not count towards the column's width, so the column took the narrower
        // button's width and the caption was cut to its first word.
        column.addView(
            TextView(service).apply {
                setText(if (cover == Cover.HOME_FEED) R.string.cover_home_text else R.string.cover_explore_text)
                setTextColor(caption)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(32), 0, dp(32), 0)
            },
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT),
        )
        if (cover == Cover.HOME_FEED) {
            column.addView(
                TextView(service).apply {
                    setText(R.string.cover_open_messages)
                    setTextColor(buttonText)
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    background = GradientDrawable().apply {
                        setColor(buttonFill)
                        cornerRadius = dp(8).toFloat()
                    }
                    setOnClickListener { onOpenMessages() }
                },
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(16) },
            )
        }
        return FrameLayout(service).apply {
            setBackgroundColor(surface)
            // Consumes touches that land on the cover itself, not only on its button.
            isClickable = true
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    onOutsideTouch()
                    true
                } else {
                    false
                }
            }
            addView(column, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        }
    }

    /**
     * See-through, over a tab button. The finger going down raises the cover at once; lifting it
     * inside the patch is a tap, passed on to the tab. A press dragged off the patch is dropped.
     */
    @SuppressLint("ClickableViewAccessibility") // The tap is passed on to Instagram's own button.
    private fun tabButtonPatch(cover: Cover): View = View(service).apply {
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> onTabDown(cover)
                MotionEvent.ACTION_UP ->
                    if (event.x in 0f..view.width.toFloat() && event.y in 0f..view.height.toFloat()) onTabUp(cover)
            }
            true
        }
    }

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()

    private companion object {
        /**
         * Instagram's dark-mode background, a blue-grey rather than black: read from the screen of
         * the test phone (Instagram 455, 14 Nov 2026). Light mode is plain white.
         */
        const val DARK_SURFACE = 0xFF0C1014.toInt()
        const val DARK_TAB_BAR = DARK_SURFACE
    }
}
