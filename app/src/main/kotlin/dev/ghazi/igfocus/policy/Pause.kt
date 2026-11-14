package dev.ghazi.igfocus.policy

/**
 * Pause is one stored timestamp: Unscroll is paused until that moment. It is compared with the
 * clock whenever it matters, so it expires by itself with no alarm or background work.
 */
object Pause {
    const val NOT_PAUSED = 0L

    /** "Until I resume": a moment that never arrives. */
    const val UNTIL_RESUMED = Long.MAX_VALUE

    fun isPaused(pauseUntilMs: Long, nowMs: Long): Boolean = nowMs < pauseUntilMs

    fun until(nowMs: Long, minutes: Int): Long = nowMs + minutes * 60_000L
}
