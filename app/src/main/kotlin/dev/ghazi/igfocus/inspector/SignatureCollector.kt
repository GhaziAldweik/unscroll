package dev.ghazi.igfocus.inspector

import dev.ghazi.igfocus.rules.InstagramRules

/** The sorted, distinct view-ID names visible on one Instagram screen. Holds no screen text. */
data class ScreenSignature(val ids: List<String>)

/**
 * The only node properties the collector may read. Text and content descriptions are
 * deliberately absent, so a signature cannot contain messages, names or captions.
 */
interface NodeView {
    val viewId: String?
    val isVisible: Boolean
    val childCount: Int
    fun child(index: Int): NodeView?
}

object SignatureCollector {
    /** A guard against runaway trees. Instagram screens are far smaller than this. */
    const val MAX_NODES = 5000

    /**
     * Walks every tree in [roots] (one per Instagram window, e.g. a screen plus a dialog on top)
     * and returns the IDs of the nodes that are visible to the user.
     */
    fun collect(roots: List<NodeView>, maxNodes: Int = MAX_NODES): ScreenSignature {
        val ids = sortedSetOf<String>()
        val pending = ArrayDeque(roots)
        var visited = 0
        while (pending.isNotEmpty() && visited < maxNodes) {
            val node = pending.removeLast()
            visited++
            if (node.isVisible) {
                node.viewId?.takeIf { it.isNotEmpty() }?.let { ids.add(InstagramRules.shortId(it)) }
            }
            // An invisible parent can still have visible children, so always descend.
            for (i in 0 until node.childCount) {
                node.child(i)?.let(pending::addLast)
            }
        }
        return ScreenSignature(ids.toList())
    }
}
