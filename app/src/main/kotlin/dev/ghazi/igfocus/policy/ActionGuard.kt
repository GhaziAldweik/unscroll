package dev.ghazi.igfocus.policy

import dev.ghazi.igfocus.service.Screen

/**
 * Keeps blocking from turning into a loop.
 *
 * - Actions are spaced at least [minIntervalMs] apart, so the scans that arrive while Instagram is
 *   still switching screens do not repeat an action that is already working.
 * - If the same screen is still showing after [maxAttemptsOnOneScreen] actions, the action is not
 *   working (a misread screen, or a tap that does nothing). Blocking then stops itself.
 *   It stays stopped for [tripMs] or until [reset].
 * - More than [maxActionsPerWindow] actions within [windowMs] slow actions down to one per
 *   [throttledIntervalMs], but never stop them. Each of those actions worked, so this only
 *   guards against two blocked screens sending the user back and forth. Stopping instead would be a
 *   way around blocking: on 14 Nov a tester bounced off ten reels in twelve seconds while testing.
 */
class ActionGuard(
    private val minIntervalMs: Long = 1_000,
    private val maxAttemptsOnOneScreen: Int = 3,
    private val maxActionsPerWindow: Int = 20,
    private val windowMs: Long = 60_000,
    private val tripMs: Long = 10 * 60_000,
    private val throttledIntervalMs: Long = 2_000,
) {
    enum class Verdict {
        ACT,

        /** Too soon after the previous action. Try again on a later scan. */
        WAIT,

        /** Stopped by an earlier trip. */
        STOPPED,

        /** This call stopped blocking. Report it once. */
        JUST_STOPPED,
    }

    var stoppedUntilMs: Long = 0
        private set

    var stoppedOn: Screen? = null
        private set

    /** How many actions in a row have been tried on the current screen without it changing. */
    var attempts = 0
        private set

    private var lastActionMs = 0L
    private var lastActionScreen: Screen? = null
    private var leftSinceLastAction = true
    private val recent = ArrayDeque<Long>()

    fun isStopped(nowMs: Long): Boolean = nowMs < stoppedUntilMs

    /** Call for every recognised screen, so an action that worked is known to have worked. */
    fun onScreen(screen: Screen) {
        if (screen != Screen.UNKNOWN && screen != lastActionScreen) leftSinceLastAction = true
    }

    /**
     * Asks to act on [screen]. If the answer is [Verdict.ACT], the action is counted as done.
     *
     * With [neverStop], an action that keeps not changing the screen is slowed down instead of
     * stopping blocking. Used for Back on a reel: Back can land on another reel (still a reel
     * viewer), and pressing Back again is then right. Stopping would hand over ten minutes of reels.
     */
    fun check(screen: Screen, nowMs: Long, neverStop: Boolean = false): Verdict {
        if (isStopped(nowMs)) return Verdict.STOPPED
        if (lastActionScreen != null && nowMs - lastActionMs < minIntervalMs) return Verdict.WAIT

        while (recent.isNotEmpty() && nowMs - recent.first() >= windowMs) recent.removeFirst()
        if (recent.size >= maxActionsPerWindow && nowMs - lastActionMs < throttledIntervalMs) return Verdict.WAIT

        var nextAttempts = if (screen == lastActionScreen && !leftSinceLastAction) attempts + 1 else 1
        if (nextAttempts > maxAttemptsOnOneScreen) {
            if (!neverStop) {
                stoppedUntilMs = nowMs + tripMs
                stoppedOn = screen
                return Verdict.JUST_STOPPED
            }
            if (nowMs - lastActionMs < throttledIntervalMs) return Verdict.WAIT
            nextAttempts = maxAttemptsOnOneScreen
        }

        attempts = nextAttempts
        lastActionScreen = screen
        lastActionMs = nowMs
        leftSinceLastAction = false
        recent.addLast(nowMs)
        return Verdict.ACT
    }

    fun reset() {
        stoppedUntilMs = 0
        stoppedOn = null
        lastActionScreen = null
        attempts = 0
        leftSinceLastAction = true
        recent.clear()
    }
}
