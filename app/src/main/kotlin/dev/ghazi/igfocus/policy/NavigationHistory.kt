package dev.ghazi.igfocus.policy

import dev.ghazi.igfocus.service.Screen
import kotlin.math.abs

/**
 * Where the user has been, as far as the rules need to know. Screen labels, times and reel-pager
 * positions only.
 *
 * `UNKNOWN` is never recorded: Instagram passes through it for a fraction of a second on most
 * transitions, and recording it would hide the screen a reel was really opened from. Nothing is
 * recorded while Instagram is not in front either, so a trip to the notification shade or Recents
 * returns to the same reel visit, still allowed.
 *
 * One exception: a reel viewer that reads as `UNKNOWN` for longer than a flicker
 * ([REOPEN_AFTER_MS]) and then shows again was closed onto a page that is not recognised, such
 * as a collection inside Saved, and a reel was opened from there. That starts a new visit, from
 * the same place as before.
 */
class NavigationHistory {
    /** The latest recognised screen. */
    var current: Screen = Screen.UNKNOWN
        private set

    /** When [current] was first seen. */
    private var currentSinceMs = 0L

    /**
     * The screen the current reel-viewer visit was opened from, or null if it is not known
     * (the service started while a reel was already showing). Null outside the reel viewer.
     */
    var reelEntry: Screen? = null
        private set

    /**
     * How long the user had been on [reelEntry] before the reel opened. A very short stay means they
     * was only passing through it, for example swiping across tabs and tapping a reel before the
     * new tab was recognised, so the screen they really tapped on is not known.
     */
    var reelEntryStayMs: Long = 0
        private set

    /** The reel pager's position when this visit began; null until its first scroll event. */
    private var pagerStartIndex: Int? = null

    /**
     * The pager has moved away from the reel this visit began on: the user swiped to another reel.
     * It stays true for the rest of the visit, so swiping fast cannot outrun it.
     */
    var reelMovedOn = false
        private set

    /** When a reel viewer started reading as UNKNOWN; null while it reads as itself. */
    private var unknownSinceMs: Long? = null

    /** Instagram has been away from the screen; the next recognised screen is where it opened. */
    private var arriving = true

    /**
     * the user is still on the screen Instagram opened on, and has not moved since. Instagram opens
     * on Home after a fresh start, or on wherever it was left; either way this is Instagram's
     * choice, not theirs, while a press on Home later is theirs.
     */
    var onOpeningScreen = false
        private set

    /** Instagram left the screen: another app, the phone's home screen, or it was closed. */
    fun onInstagramLeft() {
        arriving = true
    }

    /** Leaving the opening screen did not work; treat it as one the user chose. */
    fun forgetOpeningScreen() {
        onOpeningScreen = false
    }

    fun record(screen: Screen, nowMs: Long) {
        if (screen == Screen.UNKNOWN) {
            if (current in REEL_SCREENS && unknownSinceMs == null) unknownSinceMs = nowMs
            return
        }
        if (arriving) {
            arriving = false
            onOpeningScreen = true
        } else if (screen != current) {
            onOpeningScreen = false
        }
        val awayMs = unknownSinceMs?.let { nowMs - it } ?: 0
        unknownSinceMs = null
        if (screen == current) {
            if (screen in REEL_SCREENS && awayMs >= REOPEN_AFTER_MS) startPagerVisit()
            return
        }
        if (screen !in REEL_SCREENS || current !in REEL_SCREENS) startPagerVisit()
        // Entering from a DM reel is recorded as DM_REEL, which is not an allowed entry:
        // it means the user swiped past the reel a friend sent.
        if (screen == Screen.REEL_VIEWER) {
            reelEntry = current.takeIf { it != Screen.UNKNOWN }
            reelEntryStayMs = nowMs - currentSinceMs
        } else {
            reelEntry = null
            reelEntryStayMs = 0
        }
        current = screen
        currentSinceMs = nowMs
    }

    /**
     * A scroll event from the reel pager. Its first visible item is the reel showing, and
     * [deltaY] is how far it moved. Measured on the S25 Ultra (14 Nov), Instagram 455:
     * - a reel opening reports position 0 and no movement;
     * - a swipe to the next reel moves the pager 600–2,100 px per event;
     * - when the swipe comes right after the reel opens, the first event already reports the
     *   next reel, so a position change alone would miss that one swipe. Movement of at least
     *   [minSwipePx] therefore counts as moving on too.
     */
    fun onReelPagerScroll(fromIndex: Int, deltaY: Int, minSwipePx: Int) {
        if (current !in REEL_SCREENS) return
        if (abs(deltaY) >= minSwipePx) reelMovedOn = true
        if (fromIndex < 0) return
        val start = pagerStartIndex
        if (start == null) {
            pagerStartIndex = fromIndex
        } else if (fromIndex != start) {
            reelMovedOn = true
        }
    }

    fun reset() {
        current = Screen.UNKNOWN
        currentSinceMs = 0
        reelEntry = null
        reelEntryStayMs = 0
        unknownSinceMs = null
        arriving = true
        onOpeningScreen = false
        startPagerVisit()
    }

    private fun startPagerVisit() {
        pagerStartIndex = null
        reelMovedOn = false
    }

    companion object {
        private val REEL_SCREENS = setOf(Screen.REEL_VIEWER, Screen.DM_REEL)

        /** Longer than any UNKNOWN flicker seen inside a reel viewer (about 50–150 ms). */
        const val REOPEN_AFTER_MS = 600L
    }
}
