package dev.ghazi.igfocus.service

import kotlinx.coroutines.flow.MutableStateFlow

/** Live blocking state the service publishes for the dashboard. In memory only. */
object BlockingState {
    enum class Reason {
        /** A tap or Back on [Stopped.screen] kept repeating without the screen changing. */
        ACTION_REPEATING,

        /** Covers kept appearing and disappearing on [Stopped.screen]. */
        COVERS_FLAPPING,
    }

    /** Blocking (or only its covers) stopped itself. */
    data class Stopped(val reason: Reason, val screen: Screen, val atMs: Long, val untilMs: Long)

    val stopped = MutableStateFlow<Stopped?>(null)

    /** The parts of Instagram the user can switch off, one per switch on the Focus tab. */
    enum class Area { REELS, HOME_FEED, EXPLORE, SEARCH_RESULTS }

    /**
     * When each area last really did something since the service started: a cover drawn, a tap
     * on the DMs tab or a Back. A switch that is on but missing here has not had to act yet, which
     * is what lets the Focus tab tell "switched on" apart from "working".
     */
    val lastWorked = MutableStateFlow<Map<Area, Long>>(emptyMap())

    /** Covers are re-placed on every scan, so the time is refreshed at most every [REFRESH_MS]. */
    fun worked(area: Area, now: Long) {
        val times = lastWorked.value
        if (now - (times[area] ?: 0L) < REFRESH_MS) return
        lastWorked.value = times + (area to now)
    }

    private const val REFRESH_MS = 5_000L
}
