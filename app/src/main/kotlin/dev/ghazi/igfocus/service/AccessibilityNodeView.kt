package dev.ghazi.igfocus.service

import android.view.accessibility.AccessibilityNodeInfo
import dev.ghazi.igfocus.inspector.NodeView

/** Exposes only the ID, visibility and children of a node. See [NodeView]. */
class AccessibilityNodeView(private val node: AccessibilityNodeInfo) : NodeView {
    override val viewId: String? get() = node.viewIdResourceName
    override val isVisible: Boolean get() = node.isVisibleToUser
    override val childCount: Int get() = node.childCount
    override fun child(index: Int): NodeView? = node.getChild(index)?.let(::AccessibilityNodeView)
}
