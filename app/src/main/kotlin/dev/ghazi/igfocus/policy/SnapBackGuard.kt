package dev.ghazi.igfocus.policy

/**
 * Limits scrolling Home back up to its Stories row after the user drags the feed.
 *
 * A snap that is not followed by Home at rest counts as a failure. [maxFailures] in a row, or
 * [maxPerWindow] snaps within [windowMs], stop snapping for [stopMs]. Without this, a wrong idea
 * of "at rest" (a new row above the Stories, say) would scroll Instagram every couple of seconds
 * for ever. Snaps are at least [minIntervalMs] apart.
 */
class SnapBackGuard(
    private val minIntervalMs: Long = 1_500,
    private val maxFailures: Int = 3,
    private val maxPerWindow: Int = 6,
    private val windowMs: Long = 60_000,
    private val stopMs: Long = 10 * 60_000,
) {
    enum class Verdict { SNAP, WAIT, STOPPED, JUST_STOPPED }

    /** Snaps in a row that did not bring Home back to rest. */
    var failures = 0
        private set

    private var stoppedUntilMs = 0L
    private var lastSnapMs = 0L
    private var snapPending = false
    private val recent = ArrayDeque<Long>()

    fun isStopped(nowMs: Long): Boolean = nowMs < stoppedUntilMs

    /** Home was seen at rest, so the last snap (if any) worked. */
    fun onAtRest() {
        snapPending = false
        failures = 0
    }

    /** Asks to snap now. If the answer is [Verdict.SNAP], the snap is counted as done. */
    fun check(nowMs: Long): Verdict {
        if (isStopped(nowMs)) return Verdict.STOPPED
        if (recent.isNotEmpty() && nowMs - lastSnapMs < minIntervalMs) return Verdict.WAIT
        val nextFailures = if (snapPending) failures + 1 else failures
        while (recent.isNotEmpty() && nowMs - recent.first() >= windowMs) recent.removeFirst()
        if (nextFailures >= maxFailures || recent.size >= maxPerWindow) {
            stoppedUntilMs = nowMs + stopMs
            return Verdict.JUST_STOPPED
        }
        failures = nextFailures
        lastSnapMs = nowMs
        snapPending = true
        recent.addLast(nowMs)
        return Verdict.SNAP
    }

    fun reset() {
        stoppedUntilMs = 0
        failures = 0
        snapPending = false
        recent.clear()
    }
}
