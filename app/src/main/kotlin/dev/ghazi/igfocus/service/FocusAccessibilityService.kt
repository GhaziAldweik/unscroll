package dev.ghazi.igfocus.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dev.ghazi.igfocus.LOG_TAG
import dev.ghazi.igfocus.inspector.Capture
import dev.ghazi.igfocus.inspector.CaptureStore
import dev.ghazi.igfocus.inspector.InspectorState
import dev.ghazi.igfocus.inspector.ScreenSignature
import dev.ghazi.igfocus.inspector.SignatureCollector
import dev.ghazi.igfocus.inspector.SignatureHistory
import dev.ghazi.igfocus.policy.ActionGuard
import dev.ghazi.igfocus.policy.Box
import dev.ghazi.igfocus.policy.Cover
import dev.ghazi.igfocus.policy.CoverFlapGuard
import dev.ghazi.igfocus.policy.CoverGeometry
import dev.ghazi.igfocus.policy.Decision
import dev.ghazi.igfocus.policy.NavigationHistory
import dev.ghazi.igfocus.policy.Pause
import dev.ghazi.igfocus.policy.PolicyEngine
import dev.ghazi.igfocus.policy.SnapBackGuard
import dev.ghazi.igfocus.policy.Steer
import dev.ghazi.igfocus.rules.InstagramRules
import dev.ghazi.igfocus.settings.SettingsStore
import dev.ghazi.igfocus.settings.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Reads which Instagram screen is showing and, when a blocking rule is switched on, acts on it:
 * "Reels tab → DMs tab", "result page → Back", and covers over the Home feed and the Explore grid.
 *
 * Android delivers events from Instagram only (see accessibility_service_config.xml).
 * With Inspector mode and every blocking rule off, or while paused, the service does no work.
 *
 * Screens are recognised by asking Instagram about a handful of view IDs ([Lookup]), not by
 * reading its whole screen: each question is answered in Instagram's process, and walking every
 * view cost hundreds of milliseconds per scan on busy screens. The whole screen is read only in
 * Inspector mode, which needs every ID for captures.
 *
 * Overlays (covers and the Capture button) are shown only while an Instagram window has input
 * focus. Accessibility overlays are drawn above the notification shade, so without this a cover
 * could hide the Pause tile. Pulling the shade down, pressing Home or switching apps all move
 * focus away from Instagram, and the overlays come down within [WATCHDOG_MS].
 *
 * Reading the screen can only follow Instagram's drawing. For the Home and Search tabs the cover
 * comes first instead: see-through patches over those two buttons catch the press, put the cover
 * up and pass the tap on ([onTabDown], [onTabUp]).
 */
class FocusAccessibilityService : AccessibilityService(), CaptureOverlay.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val signatures = SignatureHistory()
    private val navigation = NavigationHistory()
    private val guard = ActionGuard()
    /** One per cover, so a flickering Explore cover never takes the Home cover down with it. */
    private val flapGuards = Cover.entries.associateWith { CoverFlapGuard() }
    private val snapGuard = SnapBackGuard()

    private val store by lazy { SettingsStore(applicationContext) }

    /**
     * The blocked-screen visit now under way, so each visit counts as one step-in however many
     * scans, covers or Backs it takes. Unrecognised screens in between do not start a new visit.
     */
    private var visitScreen: Screen? = null
    private var visitCounted = false

    private var captureOverlay: CaptureOverlay? = null
    private var covers: CoverOverlay? = null
    private var silencer: FeedSilencer? = null
    private var settings = UserSettings()
    private var scanPending = false
    private var lastScanEndMs = 0L

    /** Where each content cover last stood, so a tab press can put it up before Instagram draws. */
    private val lastBoxes = mutableMapOf<Cover, Box>()

    /** A cover put up by a tab press, kept until its screen is recognised or the time runs out. */
    private var raisedByPress: Cover? = null
    private var raisedUntilMs = 0L

    /** Set if a press could not be passed on to a tab: the patches stay off so no tab goes dead. */
    private var tabButtonsOff = false

    /** Tab-button positions per screen and tab-bar position, so steady scans skip those lookups. */
    private val tabButtonCache = mutableMapOf<Cover, Triple<Screen, Box, Box>>()

    /** Instagram's windows as of the last scan, so the watchdog can check focus cheaply. */
    private var instagramWindowIds = emptySet<Int>()

    /** Overlays were taken down because Instagram lost focus. Put them back when it returns. */
    private var overlaysSuppressed = false
    private var watchdogPosted = false

    /**
     * When Instagram was last seen on screen, focused or not (the shade does not count as leaving).
     * Leaving is seen directly only while the watchdog runs; from a screen without overlays it
     * shows up as a long silence instead ([AWAY_GAP_MS]).
     */
    private var instagramSeenMs = 0L

    /** The last logged placement per cover, so each change is logged once. */
    private val lastPlacementLog = mutableMapOf<Cover, String>()

    /** When the user last touched Instagram outside a cover, from the covers' outside-touch signal. */
    private var lastTouchMs = 0L
    private var fastPollUntilMs = 0L

    /** When Instagram last reported a scroll; a drag is over once these stop. */
    private var lastScrollMs = 0L

    private var lastSlowScanLogMs = 0L

    private val scan = Runnable {
        scanPending = false
        scanInstagram()
        lastScanEndMs = SystemClock.uptimeMillis()
    }

    /** After a touch outside the covers, read the screen every [FAST_POLL_MS] for a short while. */
    private val fastPoll = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() >= fastPollUntilMs) return
            scanInstagram()
            handler.postDelayed(this, FAST_POLL_MS)
        }
    }

    /** Runs once a drag on Home has been still for [SNAP_IDLE_MS], to snap the feed back. */
    private val afterScroll = Runnable { requestScan() }

    /**
     * Runs only while an overlay is up or waiting to come back. Events stop arriving when Instagram
     * loses focus, so this is the only way to notice the shade or another app coming to the front.
     */
    private val watchdog = Runnable {
        watchdogPosted = false
        val ids = instagramWindowIds
        val instagram = try {
            windows.filter { it.id in ids }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Could not list windows", e)
            emptyList()
        }
        when {
            instagram.isEmpty() -> {
                // Instagram is no longer on screen. Its next event will start a fresh scan.
                hideOverlays()
                overlaysSuppressed = false
                onInstagramLeft()
                return@Runnable
            }
            instagram.none { it.isFocused } -> {
                if (anyOverlayShowing()) {
                    hideOverlays()
                    Log.i(LOG_TAG, "Instagram lost focus: overlays hidden")
                }
                overlaysSuppressed = true
            }
            overlaysSuppressed -> {
                // Closing the shade may send no Instagram event, so ask for a scan ourselves.
                overlaysSuppressed = false
                requestScan()
            }
        }
        instagramSeenMs = System.currentTimeMillis()
        if (anyOverlayShowing() || overlaysSuppressed) startWatchdog()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        captureOverlay = CaptureOverlay(this, this)
        val silencer = FeedSilencer(this, handler).also { silencer = it }
        covers = CoverOverlay(
            this,
            onOpenMessages = ::openMessagesFromCover,
            onOutsideTouch = ::onOutsideTouch,
            onTabDown = ::onTabDown,
            onTabUp = ::onTabUp,
            onContentShown = { shown -> if (shown) silencer.hold() else silencer.release() },
        )
        InspectorState.serviceConnected.value = true
        BlockingState.lastWorked.value = emptyMap()
        InspectorState.recognised.value = emptySet()
        Log.i(LOG_TAG, "Accessibility service connected")

        scope.launch {
            store.settings.collect(::onSettings)
        }
        scope.launch {
            ServiceStatus.turnOffRequests.collect {
                Log.i(LOG_TAG, "Turned off from the app")
                disableSelf()
            }
        }
    }

    private fun onSettings(new: UserSettings) {
        val old = settings
        settings = new
        if (new.inspectorEnabled != old.inspectorEnabled) {
            Log.i(LOG_TAG, "Inspector mode ${if (new.inspectorEnabled) "on" else "off"}")
            if (!new.inspectorEnabled) captureOverlay?.hide()
        }
        if (new.blocking != old.blocking || new.pauseUntilMs != old.pauseUntilMs) {
            // A deliberate change by the user clears an earlier self-stop.
            guard.reset()
            flapGuards.values.forEach { it.reset() }
            snapGuard.reset()
            BlockingState.stopped.value = null
            if (!isBlocking()) covers?.hideAll()
            Log.i(LOG_TAG, "Blocking: ${new.blocking}, paused until ${new.pauseUntilMs}")
        }
        if (isWorking()) requestScan()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isWorking()) return
        if (event?.packageName?.toString() != InstagramRules.PACKAGE) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            lastScrollMs = System.currentTimeMillis()
            handler.removeCallbacks(afterScroll)
            handler.postDelayed(afterScroll, SNAP_IDLE_MS + 30)
            onReelScroll(event)
        }
        requestScan()
    }

    /**
     * One reel at a time: a scroll of the reel pager to another position means the user swiped to
     * the next reel. Only the pager counts, so scrolling the comments does not. Logs IDs and
     * positions only.
     */
    private fun onReelScroll(event: AccessibilityEvent) {
        val screen = navigation.current
        if (screen != Screen.REEL_VIEWER && screen != Screen.DM_REEL) return
        val id = try {
            event.source?.viewIdResourceName?.let(InstagramRules::shortId)
        } catch (e: Exception) {
            null
        } ?: "-"
        Log.i(
            LOG_TAG,
            "Reel scroll on $screen: $id items ${event.fromIndex}..${event.toIndex} of ${event.itemCount}, " +
                "scrollY ${event.scrollY}, dx ${event.scrollDeltaX}, dy ${event.scrollDeltaY}",
        )
        if (id !in InstagramRules.REEL_PAGERS) return
        val wasMovedOn = navigation.reelMovedOn
        navigation.onReelPagerScroll(event.fromIndex, event.scrollDeltaY, minSwipePx = dp(MIN_SWIPE_DP))
        if (navigation.reelMovedOn && !wasMovedOn) {
            Log.i(LOG_TAG, "Swiped to another reel on $screen: going back")
            handler.removeCallbacks(scan)
            scanPending = false
            scanInstagram()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        hideOverlays()
        silencer?.release()
        captureOverlay = null
        covers = null
        silencer = null
        InspectorState.serviceConnected.value = false
        Log.i(LOG_TAG, "Accessibility service stopped")
        super.onDestroy()
    }

    private fun onInstagramLeft() {
        navigation.onInstagramLeft()
        visitScreen = null
    }

    /** One step-in for the current visit, stored as a count for today. Nothing else is recorded. */
    private fun countStepIn() {
        if (visitCounted || visitScreen == null) return
        visitCounted = true
        val today = LocalDate.now().toEpochDay()
        scope.launch {
            try {
                store.addStepIn(today)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Could not store a step-in", e)
            }
        }
    }

    private fun isBlocking(): Boolean =
        settings.blocking.anyOn && !Pause.isPaused(settings.pauseUntilMs, System.currentTimeMillis())

    private fun isWorking(): Boolean = settings.inspectorEnabled || isBlocking()

    private fun anyOverlayShowing(): Boolean =
        captureOverlay?.isShowing == true || covers?.isShowing == true

    private fun hideOverlays() {
        captureOverlay?.hide()
        covers?.hideAll()
    }

    private fun startWatchdog() {
        if (watchdogPosted) return
        watchdogPosted = true
        handler.postDelayed(watchdog, WATCHDOG_MS)
    }

    private fun onOutsideTouch() {
        val now = System.currentTimeMillis()
        lastTouchMs = now
        fastPollUntilMs = now + FAST_POLL_WINDOW_MS
        handler.removeCallbacks(fastPoll)
        handler.postDelayed(fastPoll, FAST_POLL_MS)
    }

    /**
     * Coalesces a burst of events into one read. A playing video fires events constantly. The
     * first event after a quiet spell is read at once; after that, reads keep a gap that is
     * shortest while a cover is up, so it keeps up with scrolling, and short while a cover rule is
     * on. Scans only happen while Instagram is in front and sending events, so the cost is
     * bounded by Instagram's own activity.
     */
    private fun requestScan() {
        if (scanPending) return
        scanPending = true
        val gap = when {
            // Content covers only: the tab-bar patches are up on the DM inbox too.
            covers?.isShowingContent == true -> COVER_SCAN_DELAY_MS
            settings.blocking.homeFeed || settings.blocking.explore -> COVER_RULE_SCAN_DELAY_MS
            else -> SCAN_DELAY_MS
        }
        val delay = (lastScanEndMs + gap - SystemClock.uptimeMillis()).coerceIn(0, gap)
        handler.postDelayed(scan, delay)
    }

    /**
     * A finger went down on the Home or Search tab. Its cover goes up now, where it last stood,
     * before Instagram has even switched tabs; the next scans move it to the exact place. With no
     * remembered place yet (the first visit since the service started) the normal path runs.
     */
    private fun onTabDown(button: Cover) {
        val target = button.raises ?: return
        val now = System.currentTimeMillis()
        lastTouchMs = now
        fastPollUntilMs = now + FAST_POLL_WINDOW_MS
        handler.removeCallbacks(fastPoll)
        handler.postDelayed(fastPoll, FAST_POLL_MS)
        val box = lastBoxes[target] ?: return
        if (flapGuards.getValue(target).isStopped(now)) return
        if (covers?.show(target, box) != true) return
        raisedByPress = target
        raisedUntilMs = now + RAISED_HOLD_MS
        captureOverlay?.raise()
        // If the screen never changes, this scan takes the cover back down.
        handler.postDelayed(::requestScan, RAISED_HOLD_MS + 20)
    }

    /**
     * The finger lifted on the patch: pass the tap on by clicking the tab. If that ever fails,
     * the patches come off until the service restarts, so a tab can never go dead.
     */
    private fun onTabUp(button: Cover) {
        val tabId = when (button) {
            Cover.HOME_TAB_BUTTON -> InstagramRules.HOME_TAB
            Cover.SEARCH_TAB_BUTTON -> InstagramRules.SEARCH_TAB
            else -> return
        }
        val tab = Lookup(instagramWindows().map { it.second }).node(tabId)
        if (tab != null && clickSelfOrParent(tab)) {
            Log.i(LOG_TAG, "Tab press: ${button.raises} cover up, $tabId clicked")
            return
        }
        tabButtonsOff = true
        covers?.hide(Cover.HOME_TAB_BUTTON)
        covers?.hide(Cover.SEARCH_TAB_BUTTON)
        Log.w(LOG_TAG, "Tab press: could not click $tabId; tab patches off until the service restarts")
    }

    private fun scanInstagram() {
        if (!isWorking()) {
            hideOverlays()
            return
        }
        val started = SystemClock.uptimeMillis()
        val instagram = instagramWindows()
        instagramWindowIds = instagram.map { it.first.id }.toSet()
        if (instagram.isEmpty()) {
            hideOverlays()
            onInstagramLeft()
            return
        }
        val roots = instagram.map { it.second }
        val lookup = Lookup(roots)
        val screen = ScreenClassifier.classify(navigation.current, lookup::isVisible)
        val now = System.currentTimeMillis()
        if (now - instagramSeenMs >= AWAY_GAP_MS) onInstagramLeft()
        instagramSeenMs = now
        navigation.record(screen, now)
        guard.onScreen(screen)
        flapGuards.values.forEach { it.onScreen(screen) }
        // The whole screen is read only for the Inspector.
        val signature = if (settings.inspectorEnabled) collect(roots) else null
        if (signature != null && signatures.add(signature)) InspectorState.distinctScreens.value = signatures.size
        if (screen != InspectorState.lastScreen.value?.screen) {
            val sinceTouch = now - lastTouchMs
            Log.i(LOG_TAG, "Screen: $screen" + if (sinceTouch < 3_000) " ($sinceTouch ms after a touch)" else "")
            // Measurement only, once per screen change: where the other reel pager ID shows up.
            if (screen != Screen.REEL_VIEWER && screen != Screen.REELS_TAB && screen != Screen.DM_REEL &&
                lookup.isVisible(InstagramRules.REEL_PAGER_OTHER)
            ) {
                Log.i(LOG_TAG, "${InstagramRules.REEL_PAGER_OTHER} is visible on $screen")
            }
        }
        InspectorState.lastScreen.value = InspectorState.LastScreen(screen, signature?.ids?.size, now)
        if (screen != Screen.UNKNOWN && screen !in InspectorState.recognised.value) {
            InspectorState.recognised.value += screen
        }

        if (instagram.none { it.first.isFocused }) {
            // The shade or a system dialog is over Instagram: show nothing, act on nothing.
            hideOverlays()
            overlaysSuppressed = true
            startWatchdog()
            return
        }

        if (settings.inspectorEnabled) captureOverlay?.show() else captureOverlay?.hide()

        val decision = if (isBlocking()) {
            PolicyEngine.decide(screen, navigation, settings.blocking, paused = false)
        } else {
            Decision.NONE
        }
        if (screen != Screen.UNKNOWN && screen != visitScreen) {
            visitScreen = screen
            visitCounted = false
        }
        applyCovers(decision.covers, screen, lookup)
        // This screen's own content cover, not one raised early by a tab press on the screen before.
        if (decision.covers.any { it.hidesContent } && covers?.isShowingContent == true) countStepIn()
        decision.steer?.let { act(it, screen, lookup) }

        if (anyOverlayShowing()) startWatchdog()

        val took = SystemClock.uptimeMillis() - started
        if (took >= SLOW_SCAN_MS && now - lastSlowScanLogMs > 5_000) {
            lastSlowScanLogMs = now
            Log.i(LOG_TAG, "Slow scan: $took ms on $screen (${lookup.asked} lookups)")
        }
    }

    private class Placement(val box: Box?, val anchors: List<Box?>)

    private fun applyCovers(wanted: Set<Cover>, screen: Screen, lookup: Lookup) {
        val controller = covers ?: return
        val now = System.currentTimeMillis()
        val allowed = wanted.filter { cover ->
            !flapGuards.getValue(cover).isStopped(now) && !(cover.raises != null && tabButtonsOff)
        }
        val placements = allowed.associateWith { place(it, lookup, screen) }
        val boxes = HashMap<Cover, Box>()
        for ((cover, placement) in placements) {
            val box = placement.box ?: continue
            boxes[cover] = box
            if (cover.hidesContent) lastBoxes[cover] = box
        }
        // A cover raised by a tab press stays until its own screen places it, or its time is up.
        raisedByPress?.let { raised ->
            if (raised in boxes || now >= raisedUntilMs) {
                raisedByPress = null
            } else {
                controller.boxOf(raised)?.let { boxes[raised] = it }
            }
        }
        val added = controller.reconcile(boxes)
        for (cover in added) {
            val flapGuard = flapGuards.getValue(cover)
            if (flapGuard.onCoverAdded(screen, now)) {
                controller.hide(cover)
                Log.w(LOG_TAG, "Cover $cover stopped itself: it kept reappearing on $screen")
                BlockingState.stopped.value = BlockingState.Stopped(
                    BlockingState.Reason.COVERS_FLAPPING, screen, now, flapGuard.stoppedUntilMs,
                )
            }
        }
        // A new cover is drawn over the Capture button; put the button back on top.
        if (added.isNotEmpty()) captureOverlay?.raise()
        for (cover in boxes.keys) {
            val area = when (cover) {
                Cover.HOME_FEED -> BlockingState.Area.HOME_FEED
                Cover.EXPLORE_GRID -> BlockingState.Area.EXPLORE
                Cover.REELS_TAB_BUTTON -> BlockingState.Area.REELS
                // The Home and Search patches only speed up the covers above; they hide nothing.
                Cover.HOME_TAB_BUTTON, Cover.SEARCH_TAB_BUTTON -> null
            }
            if (area != null && controller.boxOf(cover) != null) BlockingState.worked(area, now)
        }

        placements[Cover.HOME_FEED]?.takeIf { it.box != null }?.let { snapBackIfScrolled(it, lookup, now) }
    }

    /**
     * Home under Unscroll is "Stories + cover". Dragging the Stories row scrolls the hidden feed,
     * which moves the Stories away and makes the cover chase them. Once the drag is over, scroll
     * the feed back to the top.
     */
    private fun snapBackIfScrolled(home: Placement, lookup: Lookup, now: Long) {
        val tray = home.anchors.getOrNull(0)
        val topBar = home.anchors.getOrNull(1)
        if (!CoverGeometry.isFeedScrolledUp(tray, topBar, dp(SNAP_SLOP_DP))) {
            snapGuard.onAtRest()
            return
        }
        val sinceScroll = now - lastScrollMs
        // Still dragging: afterScroll comes back once it stops.
        if (sinceScroll < SNAP_IDLE_MS) return
        // No drag just now: the Stories also leave the screen while a story is opening, and
        // tapping Home then would interrupt it. Only a recent drag counts.
        if (sinceScroll > SNAP_RECENT_MS) return
        when (snapGuard.check(now)) {
            SnapBackGuard.Verdict.SNAP -> scrollFeedToTop(lookup)
            SnapBackGuard.Verdict.JUST_STOPPED -> Log.w(LOG_TAG, "Snap-back stopped itself: Home never came back to rest")
            SnapBackGuard.Verdict.WAIT, SnapBackGuard.Verdict.STOPPED -> Unit
        }
    }

    /** First a gentle scroll back; if that did not reach the top, Home's own tab. */
    private fun scrollFeedToTop(lookup: Lookup) {
        val list = lookup.node(InstagramRules.HOME_FEED_LIST)
        val scrolled = snapGuard.failures == 0 &&
            list?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.id) == true
        if (scrolled) {
            Log.i(LOG_TAG, "Snap-back: scrolled the feed back")
            return
        }
        val homeTab = lookup.node(InstagramRules.HOME_TAB)
        if (homeTab != null && clickSelfOrParent(homeTab)) {
            Log.i(LOG_TAG, "Snap-back: tapped the Home tab (attempt ${snapGuard.failures + 1})")
        } else {
            Log.w(LOG_TAG, "Snap-back: could not scroll the feed back")
        }
    }

    /** Where [cover] goes on this screen; a null box means its anchors are missing (no cover). */
    private fun place(cover: Cover, lookup: Lookup, screen: Screen): Placement {
        val anchorIds = when (cover) {
            Cover.HOME_FEED -> InstagramRules.HOME_FEED_TOP_ANCHORS
            Cover.EXPLORE_GRID -> InstagramRules.EXPLORE_GRID_TOP_ANCHORS
            Cover.REELS_TAB_BUTTON -> return Placement(tabButton(cover, InstagramRules.REELS_TAB_BUTTON, lookup, screen), emptyList())
            Cover.HOME_TAB_BUTTON -> return Placement(tabButton(cover, InstagramRules.HOME_TAB, lookup, screen), emptyList())
            Cover.SEARCH_TAB_BUTTON -> return Placement(tabButton(cover, InstagramRules.SEARCH_TAB, lookup, screen), emptyList())
        }
        val anchors = anchorIds.map { lookup.bounds(it.id) }
        val edges = anchorIds.zip(anchors).map { (anchor, b) ->
            when (anchor.edge) {
                InstagramRules.Edge.TOP -> b?.top
                InstagramRules.Edge.BOTTOM -> b?.bottom
            }
        }
        val tabBar = lookup.bounds(InstagramRules.TAB_BAR)
        val box = CoverGeometry.between(edges, tabBar, minHeight = dp(MIN_COVER_DP))

        // IDs and rectangles only, logged when the placement changes, to tune the anchors.
        val line = anchorIds.zip(anchors).joinToString(" ") { (anchor, b) -> "${anchor.id}=${b ?: "-"}" } +
            " ${InstagramRules.TAB_BAR}=${tabBar ?: "-"} → ${box ?: "no cover"}"
        if (lastPlacementLog[cover] != line) {
            lastPlacementLog[cover] = line
            Log.i(LOG_TAG, "Cover $cover: $line")
        }
        return Placement(box, anchors)
    }

    /**
     * Exactly over the tab button [id]; with no tab bar on screen there is no button and no patch.
     * Each lookup costs a scan about 10 ms, so a button is looked up again only when the screen
     * or the tab bar's position changes.
     */
    private fun tabButton(cover: Cover, id: String, lookup: Lookup, screen: Screen): Box? {
        val bar = lookup.bounds(InstagramRules.TAB_BAR) ?: return null
        tabButtonCache[cover]?.let { (cachedScreen, cachedBar, button) ->
            if (cachedScreen == screen && cachedBar == bar) return button
        }
        val button = lookup.bounds(id) ?: return null
        tabButtonCache[cover] = Triple(screen, bar, button)
        return button
    }

    private fun act(steer: Steer, screen: Screen, lookup: Lookup) {
        val now = System.currentTimeMillis()
        // Back on a reel may land on another reel; pressing Back again is then right, so this
        // one never stops itself (see ActionGuard.check).
        val onReel = steer == Steer.BACK && (screen == Screen.REEL_VIEWER || screen == Screen.DM_REEL)
        // Instagram opening on Home: if the DMs tab will not open, give up on that alone below
        // rather than stopping every rule.
        val opening = steer == Steer.OPEN_MESSAGES && screen == Screen.HOME
        when (guard.check(screen, now, neverStop = onReel || opening)) {
            ActionGuard.Verdict.ACT -> countStepIn()
            ActionGuard.Verdict.WAIT, ActionGuard.Verdict.STOPPED -> return
            ActionGuard.Verdict.JUST_STOPPED -> {
                Log.w(LOG_TAG, "Blocking stopped itself: $steer on $screen kept repeating")
                BlockingState.stopped.value = BlockingState.Stopped(
                    BlockingState.Reason.ACTION_REPEATING, screen, now, guard.stoppedUntilMs,
                )
                return
            }
        }
        val area = when (screen) {
            Screen.REELS_TAB, Screen.REEL_VIEWER, Screen.DM_REEL -> BlockingState.Area.REELS
            Screen.HOME -> BlockingState.Area.HOME_FEED
            Screen.SEARCH_RESULTS -> BlockingState.Area.SEARCH_RESULTS
            else -> null
        }
        area?.let { BlockingState.worked(it, now) }
        when (steer) {
            Steer.OPEN_MESSAGES -> {
                if (opening) Log.i(LOG_TAG, "Instagram opened on Home: going to messages")
                openMessages(screen, lookup)
                if (opening && guard.attempts >= MAX_OPENING_TRIES) {
                    navigation.forgetOpeningScreen()
                    Log.w(LOG_TAG, "Instagram opened on Home: messages did not open, staying on Home")
                }
            }
            Steer.BACK -> {
                Log.i(LOG_TAG, "Steer: $screen → Back")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    /**
     * Taps the DMs tab. The first attempt clicks the view; if the screen did not change, later
     * attempts tap its position on screen instead. Back is the last resort, because from the
     * Reels tab it leads Home rather than to messages. From Home itself, Back would close
     * Instagram, so there is no last resort there.
     */
    private fun openMessages(screen: Screen, lookup: Lookup) {
        val tab = lookup.node(InstagramRules.DM_TAB)
        // Why the DMs tab could not be opened, or null (and return) once it was.
        val failure = when {
            tab == null -> "DMs tab not found"
            guard.attempts <= 1 && clickSelfOrParent(tab) -> null.also {
                Log.i(LOG_TAG, "Steer: $screen → clicked the DMs tab")
            }
            tapCentre(tab) -> null.also {
                Log.i(LOG_TAG, "Steer: $screen → tapped the DMs tab's position (attempt ${guard.attempts})")
            }
            else -> "could not tap the DMs tab"
        } ?: return
        if (screen == Screen.HOME) {
            Log.w(LOG_TAG, "Steer: $screen → $failure")
        } else {
            Log.w(LOG_TAG, "Steer: $screen → $failure, pressing Back instead")
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    /** The "Open messages" button on the Home cover. the user's own tap, so no ActionGuard. */
    private fun openMessagesFromCover() {
        val tab = Lookup(instagramWindows().map { it.second }).node(InstagramRules.DM_TAB) ?: return
        if (!clickSelfOrParent(tab)) tapCentre(tab)
        Log.i(LOG_TAG, "Cover button: opened messages")
    }

    /**
     * Questions about Instagram's screen for one scan, each asked at most once. A lookup by ID is
     * answered inside Instagram's process, far cheaper than fetching every view across processes.
     */
    private inner class Lookup(private val roots: List<AccessibilityNodeInfo>) {
        private val nodes = HashMap<String, AccessibilityNodeInfo?>()

        /** How many lookups this scan has made, for the slow-scan log. */
        var asked = 0
            private set

        /** The first view with [shortId] that is visible to the user, or null. */
        fun node(shortId: String): AccessibilityNodeInfo? {
            if (nodes.containsKey(shortId)) return nodes[shortId]
            asked++
            return findVisible(roots, shortId).also { nodes[shortId] = it }
        }

        fun isVisible(shortId: String): Boolean = node(shortId) != null

        fun bounds(shortId: String): Box? {
            val node = node(shortId) ?: return null
            val rect = Rect()
            node.getBoundsInScreen(rect)
            return if (rect.isEmpty) null else Box(rect.left, rect.top, rect.right, rect.bottom)
        }
    }

    private fun findVisible(roots: List<AccessibilityNodeInfo>, shortId: String): AccessibilityNodeInfo? = try {
        roots.asSequence()
            .flatMap { it.findAccessibilityNodeInfosByViewId(InstagramRules.fullId(shortId)).asSequence() }
            .firstOrNull { it.isVisibleToUser }
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Could not look up $shortId", e)
        null
    }

    /** Clicks [node], or its nearest clickable parent. */
    private fun clickSelfOrParent(node: AccessibilityNodeInfo): Boolean = try {
        var target: AccessibilityNodeInfo? = node
        var depth = 0
        while (target != null && !target.isClickable && depth < MAX_CLICK_ANCESTORS) {
            target = target.parent
            depth++
        }
        target != null && target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Could not click", e)
        false
    }

    /** A short tap gesture at the centre of [node]'s bounds. Reads bounds only, never content. */
    private fun tapCentre(node: AccessibilityNodeInfo): Boolean = try {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            false
        } else {
            val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
            val tap = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_MS))
                .build()
            dispatchGesture(tap, null, null)
        }
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Could not dispatch a tap", e)
        false
    }

    /** Every view-ID name visible in [roots], or null if the windows could not be read. */
    private fun collect(roots: List<AccessibilityNodeInfo>): ScreenSignature? = try {
        SignatureCollector.collect(roots.map(::AccessibilityNodeView))
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Could not read Instagram's window", e)
        null
    }

    /**
     * Instagram's own windows with their roots, found by package. `rootInActiveWindow` is avoided
     * because the active window can be one of this app's overlays. The roots are fetched without
     * prefetching: by default Android also fetches dozens of views below each root, which every
     * scan then throws away, since the lookups ask Instagram directly.
     */
    private fun instagramWindows(): List<Pair<AccessibilityWindowInfo, AccessibilityNodeInfo>> = try {
        windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { window -> window.getRoot(NO_PREFETCH)?.let { window to it } }
            .filter { it.second.packageName?.toString() == InstagramRules.PACKAGE }
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Could not list windows", e)
        emptyList()
    }

    /** The signature taken when Capture was tapped, held until a label is chosen. */
    private var pendingCapture: ScreenSignature? = null

    override fun onCaptureRequested(): Boolean {
        val roots = instagramWindows().map { it.second }
        pendingCapture = if (roots.isEmpty()) null else collect(roots)
        return pendingCapture != null
    }

    override fun onLabelChosen(label: String) {
        val signature = pendingCapture ?: return
        pendingCapture = null
        CaptureStore.get(this).add(
            Capture(
                label = label,
                timestampMs = System.currentTimeMillis(),
                instagramVersion = InstagramInfo.installedVersion(this) ?: "unknown",
                ids = signature.ids,
            )
        )
        // Counts only. View IDs are in the capture file, and screen text is never read.
        Log.i(LOG_TAG, "Captured \"$label\": ${signature.ids.size} view IDs")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val SCAN_DELAY_MS = 150L
        const val COVER_RULE_SCAN_DELAY_MS = 80L
        const val COVER_SCAN_DELAY_MS = 40L
        const val WATCHDOG_MS = 250L
        const val FAST_POLL_MS = 30L
        const val FAST_POLL_WINDOW_MS = 1_500L
        const val RAISED_HOLD_MS = 1_500L
        const val SNAP_IDLE_MS = 400L
        const val SNAP_RECENT_MS = 4_000L
        const val SLOW_SCAN_MS = 60L
        const val MAX_CLICK_ANCESTORS = 4

        /**
         * This long without seeing Instagram means it was left. Long enough that reading a chat
         * quietly is not mistaken for leaving; returning sooner lands where Instagram was anyway.
         */
        const val AWAY_GAP_MS = 60_000L

        /** Taps on the DMs tab when Instagram opens on Home, before staying on Home instead. */
        const val MAX_OPENING_TRIES = 3

        /** `AccessibilityWindowInfo.getRoot` strategy: fetch the root view alone. */
        const val NO_PREFETCH = 0
        const val TAP_MS = 50L

        /** A cover shorter than this means the anchor was not the Stories row or search bar. */
        const val MIN_COVER_DP = 120

        /** How far the Stories tray may sit above the top bar's edge and still count as at rest. */
        const val SNAP_SLOP_DP = 8

        /** A reel-pager move this far is a swipe to another reel (swipes measured 170 dp and up). */
        const val MIN_SWIPE_DP = 64
    }
}
