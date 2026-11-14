package dev.ghazi.igfocus.inspector

import dev.ghazi.igfocus.service.Screen
import kotlinx.coroutines.flow.MutableStateFlow

/** Live state the service publishes for the Diagnostics screen. In memory only. */
object InspectorState {
    /** [idCount] is known only in Inspector mode, which reads the whole screen. */
    data class LastScreen(val screen: Screen, val idCount: Int?, val timestampMs: Long)

    /** True while Android has the accessibility service bound, i.e. it is really running. */
    val serviceConnected = MutableStateFlow(false)

    val lastScreen = MutableStateFlow<LastScreen?>(null)

    val distinctScreens = MutableStateFlow(0)

    /**
     * The kinds of Instagram screen recognised since the service started (labels only). Seeing
     * Instagram with none recognised means an Instagram update has likely renamed its views.
     */
    val recognised = MutableStateFlow<Set<Screen>>(emptySet())
}
