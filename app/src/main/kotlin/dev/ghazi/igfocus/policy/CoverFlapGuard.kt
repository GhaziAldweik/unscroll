package dev.ghazi.igfocus.policy

import dev.ghazi.igfocus.service.Screen

/**
 * Stops one cover that keeps disappearing and reappearing on the same screen. That would mean
 * the cover is changing how Instagram's screen reads (Home, then unrecognised, then Home again),
 * which flickers and wastes battery.
 *
 * Only a re-add with no other recognised screen in between counts. Moving between screens, even
 * very fast (on 14 Nov a tester bounced off ten reels in twelve seconds), never counts, and stopping
 * a cover then would hand the user the very screen it hides. [maxFlaps] such re-adds within
 * [windowMs] stop this cover for [stopMs].
 */
class CoverFlapGuard(
    private val maxFlaps: Int = 5,
    private val windowMs: Long = 5_000,
    private val stopMs: Long = 60_000,
) {
    var stoppedUntilMs: Long = 0
        private set

    private var lastAddedOn: Screen? = null
    private var leftSinceAdd = true
    private val flaps = ArrayDeque<Long>()

    fun isStopped(nowMs: Long): Boolean = nowMs < stoppedUntilMs

    /** Call for every scan, so a visit to another screen is known. */
    fun onScreen(screen: Screen) {
        if (screen != Screen.UNKNOWN && screen != lastAddedOn) leftSinceAdd = true
    }

    /** Call each time this cover is put up on [screen]. Returns true if this one stopped it. */
    fun onCoverAdded(screen: Screen, nowMs: Long): Boolean {
        val flap = !leftSinceAdd && screen == lastAddedOn
        lastAddedOn = screen
        leftSinceAdd = false
        if (!flap) {
            flaps.clear()
            return false
        }
        while (flaps.isNotEmpty() && nowMs - flaps.first() >= windowMs) flaps.removeFirst()
        flaps.addLast(nowMs)
        if (flaps.size < maxFlaps) return false
        stoppedUntilMs = nowMs + stopMs
        flaps.clear()
        return true
    }

    fun reset() {
        stoppedUntilMs = 0
        lastAddedOn = null
        leftSinceAdd = true
        flaps.clear()
    }
}
